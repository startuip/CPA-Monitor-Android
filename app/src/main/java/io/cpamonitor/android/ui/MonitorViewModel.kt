package io.cpamonitor.android.ui

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import io.cpamonitor.android.alerts.MonitorWorker
import io.cpamonitor.android.data.AccountBundle
import io.cpamonitor.android.data.ConnectionException
import io.cpamonitor.android.data.MonitorRepository
import io.cpamonitor.android.data.local.PreferencesStore
import io.cpamonitor.android.data.local.UserSettings
import io.cpamonitor.android.data.remote.AnalyticsDto
import io.cpamonitor.android.data.remote.AnalyticsFilters
import io.cpamonitor.android.data.remote.DashboardDto
import io.cpamonitor.android.data.remote.EventDto
import io.cpamonitor.android.data.remote.ModelPriceDto
import io.cpamonitor.android.data.remote.StatusDto
import io.cpamonitor.android.domain.RangePreset
import io.cpamonitor.android.domain.currentRange
import java.time.LocalDate
import javax.inject.Inject
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class MonitorUiState(
    val loading: Boolean = false,
    val refreshing: Boolean = false,
    val offline: Boolean = false,
    val message: String? = null,
    val dashboard: DashboardDto? = null,
    val dashboardUpdatedAt: Long = 0,
    val analytics: AnalyticsDto? = null,
    val analyticsUpdatedAt: Long = 0,
    val accounts: AccountBundle? = null,
    val accountsUpdatedAt: Long = 0,
    val status: StatusDto? = null,
    val statusLoading: Boolean = false,
    val statusError: String? = null,
    val events: List<EventDto> = emptyList(),
    val eventsTotal: Long = 0,
    val eventsHasMore: Boolean = false,
    val eventsLoading: Boolean = false,
    val prices: Map<String, ModelPriceDto> = emptyMap(),
    val rangePreset: RangePreset = RangePreset.TODAY,
    val providerFilter: String = "",
    val modelFilter: String = "",
    val accountFilter: String = "",
    val failedOnly: Boolean = false,
    val customStart: LocalDate? = null,
    val customEnd: LocalDate? = null,
    val focusedAccountRow: String = "",
)

data class ConnectUiState(
    val connecting: Boolean = false,
    val error: String? = null,
)

@HiltViewModel
class MonitorViewModel @Inject constructor(
    private val repository: MonitorRepository,
    private val preferences: PreferencesStore,
    @ApplicationContext private val context: Context,
) : ViewModel() {
    val connected: StateFlow<Boolean> = preferences.hasConnection.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        false,
    )
    val baseUrl: StateFlow<String?> = preferences.baseUrl.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        null,
    )
    val settings: StateFlow<UserSettings> = preferences.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        UserSettings(),
    )

    private val _ui = MutableStateFlow(MonitorUiState())
    val ui = _ui.asStateFlow()
    private val _connectUi = MutableStateFlow(ConnectUiState())
    val connectUi = _connectUi.asStateFlow()
    private var beforeMs: Long? = null
    private var beforeId: Long? = null
    private var analyticsRequestId = 0L
    private var eventsRequestId = 0L
    private var connectionAvailable = false
    private var connectionEpoch = 0L
    private var refreshJob: Job? = null
    private var eventsJob: Job? = null

    init {
        viewModelScope.launch {
            preferences.hasConnection.collect { hasConnection ->
                connectionAvailable = hasConnection
                if (hasConnection && _ui.value.dashboard == null) loadCachedThenRefresh()
            }
        }
    }

    fun connect(url: String, key: String) {
        if (_connectUi.value.connecting) return
        viewModelScope.launch {
            _connectUi.value = ConnectUiState(connecting = true)
            runCatching { repository.validateAndSave(url, key) }
                .onSuccess {
                    connectionEpoch++
                    connectionAvailable = true
                    _connectUi.value = ConnectUiState()
                    MonitorWorker.schedule(context)
                    loadCachedThenRefresh(connectionEpoch)
                }
                .onFailure { error ->
                    _connectUi.value = ConnectUiState(error = error.userMessage())
                }
        }
    }

    fun refreshAll(refreshEventList: Boolean = true) {
        if (_ui.value.refreshing || !connectionAvailable) return
        val epoch = connectionEpoch
        _ui.update {
            it.copy(
                refreshing = true,
                message = null,
                statusLoading = true,
                statusError = null,
            )
        }
        refreshJob = viewModelScope.launch {
            val errors = mutableListOf<Throwable>()
            listOf(
                async { runCatching { refreshDashboard(epoch) }.onFailure(errors::add) },
                async { runCatching { refreshUsage(epoch) }.onFailure(errors::add) },
                async { runCatching { refreshAccounts(epoch) }.onFailure(errors::add) },
                async { runCatching { refreshStatus(epoch) }.onFailure(errors::add) },
                async {
                    runCatching { repository.modelPrices().prices }
                        .onSuccess { prices ->
                            if (isCurrent(epoch)) _ui.update { it.copy(prices = prices) }
                        }
                        .onFailure(errors::add)
                },
            ).awaitAll()
            if (!isCurrent(epoch)) return@launch
            _ui.update {
                it.copy(
                    refreshing = false,
                    loading = false,
                    offline = errors.isNotEmpty(),
                    message = errors.firstOrNull()?.userMessage(),
                )
            }
            if (refreshEventList) refreshEvents(reset = true)
        }
    }

    fun refreshEvents(reset: Boolean) {
        if (!connectionAvailable || !reset && _ui.value.eventsLoading) return
        if (reset) eventsJob?.cancel()
        val epoch = connectionEpoch
        val requestId = if (reset) ++eventsRequestId else eventsRequestId
        val requestRange = activeRange()
        val requestFilters = filters()
        val requestBeforeMs = if (reset) null else beforeMs
        val requestBeforeId = if (reset) null else beforeId
        eventsJob = viewModelScope.launch {
            if (reset) {
                beforeMs = null
                beforeId = null
            }
            _ui.update { it.copy(eventsLoading = true, message = null) }
            runCatching {
                repository.events(requestRange, requestFilters, requestBeforeMs, requestBeforeId)
            }.onSuccess { page ->
                if (requestId != eventsRequestId || !isCurrent(epoch)) return@onSuccess
                beforeMs = page.nextBeforeMs.takeIf { page.hasMore }
                beforeId = page.nextBeforeId.takeIf { page.hasMore }
                _ui.update {
                    it.copy(
                        events = if (reset) page.items else it.events + page.items,
                        eventsTotal = page.totalCount,
                        eventsHasMore = page.hasMore,
                        eventsLoading = false,
                        offline = false,
                    )
                }
            }.onFailure {
                if (requestId != eventsRequestId || !isCurrent(epoch)) return@onFailure
                val message = it.userMessage()
                _ui.update { state -> state.copy(eventsLoading = false, offline = true, message = message) }
            }
        }
    }

    fun setRange(value: RangePreset) {
        _ui.update { it.copy(rangePreset = value) }
        val epoch = connectionEpoch
        viewModelScope.launch {
            runCatching { refreshUsage(epoch) }.onFailure { if (isCurrent(epoch)) showError(it) }
            refreshEvents(reset = true)
        }
    }

    fun setCustomRange(start: LocalDate, endInclusive: LocalDate) {
        _ui.update {
            it.copy(
                rangePreset = RangePreset.CUSTOM,
                customStart = minOf(start, endInclusive),
                customEnd = maxOf(start, endInclusive),
            )
        }
        val epoch = connectionEpoch
        viewModelScope.launch {
            runCatching { refreshUsage(epoch) }.onFailure { if (isCurrent(epoch)) showError(it) }
            refreshEvents(reset = true)
        }
    }

    fun setProvider(value: String) = updateFilters { copy(providerFilter = value) }
    fun setModel(value: String) = updateFilters { copy(modelFilter = value) }
    fun setAccount(value: String) = updateFilters { copy(accountFilter = value) }
    fun setFailedOnly(value: Boolean) = updateFilters { copy(failedOnly = value) }

    fun applyDeepLink(path: String?, failedOnly: Boolean, accountRow: String?) {
        _ui.update {
            it.copy(
                failedOnly = if (path == "requests") failedOnly else it.failedOnly,
                focusedAccountRow = if (path == "accounts") accountRow.orEmpty() else "",
            )
        }
    }

    fun updateSettings(value: UserSettings) {
        viewModelScope.launch {
            preferences.updateSettings(value)
            if (value.alertsEnabled) MonitorWorker.schedule(context) else MonitorWorker.cancel(context)
        }
    }

    fun clearCache() {
        connectionEpoch++
        refreshJob?.cancel()
        eventsJob?.cancel()
        analyticsRequestId++
        eventsRequestId++
        val epoch = connectionEpoch
        viewModelScope.launch {
            repository.clearCache()
            if (isCurrent(epoch)) {
                beforeMs = null
                beforeId = null
                _ui.value = MonitorUiState(message = "缓存已清除")
            }
        }
    }

    fun disconnect() {
        connectionEpoch++
        connectionAvailable = false
        refreshJob?.cancel()
        eventsJob?.cancel()
        analyticsRequestId++
        eventsRequestId++
        beforeMs = null
        beforeId = null
        _ui.value = MonitorUiState()
        viewModelScope.launch {
            MonitorWorker.cancel(context)
            repository.disconnect()
        }
    }

    fun dismissMessage() {
        _ui.update { it.copy(message = null) }
        _connectUi.value = _connectUi.value.copy(error = null)
    }

    private fun updateFilters(transform: MonitorUiState.() -> MonitorUiState) {
        _ui.update { it.transform() }
        val epoch = connectionEpoch
        viewModelScope.launch {
            runCatching { refreshUsage(epoch) }.onFailure { if (isCurrent(epoch)) showError(it) }
            refreshEvents(reset = true)
        }
    }

    private suspend fun loadCachedThenRefresh(epoch: Long = connectionEpoch) {
        if (!isCurrent(epoch)) return
        _ui.update { it.copy(loading = true) }
        repository.cachedDashboard()?.let {
            if (isCurrent(epoch)) {
                _ui.update { state -> state.copy(dashboard = it.value, dashboardUpdatedAt = it.updatedAtMs) }
            }
        }
        repository.cachedAnalytics()?.let {
            if (isCurrent(epoch)) {
                _ui.update { state -> state.copy(analytics = it.value, analyticsUpdatedAt = it.updatedAtMs) }
            }
        }
        repository.cachedAccounts()?.let {
            if (isCurrent(epoch)) {
                _ui.update { state -> state.copy(accounts = it.value, accountsUpdatedAt = it.updatedAtMs) }
            }
        }
        if (isCurrent(epoch)) refreshAll()
    }

    private suspend fun refreshDashboard(epoch: Long = connectionEpoch) {
        repository.dashboard().let {
            if (isCurrent(epoch)) {
                _ui.update { state -> state.copy(dashboard = it.value, dashboardUpdatedAt = it.updatedAtMs) }
            }
        }
    }

    private suspend fun refreshUsage(epoch: Long = connectionEpoch) {
        val requestId = ++analyticsRequestId
        val requestRange = activeRange()
        val requestFilters = filters()
        repository.analytics(requestRange, requestFilters).let {
            if (requestId == analyticsRequestId && isCurrent(epoch)) {
                _ui.update { state -> state.copy(analytics = it.value, analyticsUpdatedAt = it.updatedAtMs) }
            }
        }
    }

    private suspend fun refreshStatus(epoch: Long = connectionEpoch) {
        try {
            val status = repository.status()
            if (isCurrent(epoch)) {
                _ui.update { it.copy(status = status, statusLoading = false, statusError = null) }
            }
        } catch (error: Throwable) {
            if (isCurrent(epoch)) {
                val message = error.userMessage()
                _ui.update { it.copy(statusLoading = false, statusError = message) }
            }
            throw error
        }
    }

    fun markNotificationPermissionAsked() {
        viewModelScope.launch { preferences.markNotificationPermissionAsked() }
    }

    private suspend fun refreshAccounts(epoch: Long = connectionEpoch) {
        repository.accountsAndQuotas().let {
            if (isCurrent(epoch)) {
                _ui.update { state -> state.copy(accounts = it.value, accountsUpdatedAt = it.updatedAtMs) }
            }
        }
    }

    private fun isCurrent(epoch: Long): Boolean = connectionAvailable && epoch == connectionEpoch

    private fun filters() = AnalyticsFilters(
        models = _ui.value.modelFilter.takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList(),
        providers = _ui.value.providerFilter.takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList(),
        accounts = _ui.value.accountFilter.takeIf(String::isNotBlank)?.let(::listOf) ?: emptyList(),
        failedOnly = _ui.value.failedOnly,
    )

    private fun activeRange() = currentRange(
        preset = _ui.value.rangePreset,
        customStart = _ui.value.customStart,
        customEndInclusive = _ui.value.customEnd,
    )

    private fun showError(error: Throwable) {
        val message = error.userMessage()
        _ui.update { it.copy(offline = true, message = message) }
    }
}

private fun Throwable.userMessage(): String = sanitizeSummary(when (this) {
    is ConnectionException, is IllegalArgumentException -> message ?: "操作失败"
    else -> message?.takeIf { it.length < 120 } ?: "网络请求失败，已保留离线数据"
})
