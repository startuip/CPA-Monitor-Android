package io.cpamonitor.android.domain

import io.cpamonitor.android.data.remote.QuotaTargetDto
import java.security.MessageDigest
import java.util.Base64
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive

@Serializable
data class Account(
    val rowKey: String,
    val name: String,
    val provider: String,
    val authIndex: String,
    val accountId: String,
    val projectId: String,
    val disabled: Boolean,
    val source: String,
    val accountSnapshot: String = "",
    val label: String = "",
    val planType: String = "",
) {
    fun quotaTarget() = QuotaTargetDto(
        accountSnapshot = accountSnapshot.ifBlank { name },
        authLabelSnapshot = label.ifBlank { name },
        authFileSnapshot = source,
        authProviderSnapshot = provider,
        authAccountIdSnapshot = accountId,
        authProjectIdSnapshot = projectId,
        authIndex = authIndex,
        source = source,
    )
}

fun JsonObject.toAccount(): Account {
    val runtimeId = string("id")
    val source = string("name")
    val provider = normalizeProvider(string("provider", "type")).ifBlank { "unknown" }
    // Runtime `id` is a selector, not a credential identity. CPAMP deliberately
    // does not use it as auth_index or account_id when calculating quota keys.
    val authIndex = string("auth_index", "authIndex", "auth-index")
    val accountId = if (provider == "codex") codexAccountId() else recursiveGenericAccountId()
    val projectId = if (provider == "codex") "" else
        string("project_id", "projectId", "gemini_virtual_project", "geminiVirtualProject")
    val accountSnapshot = string("account", "email", "display_account", "displayAccount")
    val label = string("label", "note")
    val name = firstNonBlank(label, accountSnapshot, source, runtimeId, authIndex, "未命名账号")
    val identity = listOf(provider, source, authIndex, accountId, projectId, accountSnapshot, label)
        .joinToString("|")
    return Account(
        rowKey = identity.sha256().take(24),
        name = name,
        provider = provider,
        authIndex = authIndex,
        accountId = accountId,
        projectId = projectId,
        disabled = boolean("disabled", "unavailable") ||
            string("status", "state").lowercase() in setOf("disabled", "inactive"),
        source = source,
        accountSnapshot = accountSnapshot,
        label = label,
        planType = resolvePlanType(provider),
    )
}

private fun JsonObject.resolvePlanType(provider: String): String {
    val metadata = get("metadata") as? JsonObject
    val attributes = get("attributes") as? JsonObject
    val containers = listOfNotNull(this, metadata, attributes)
    val tokenPlans = containers.map { container ->
        firstMeaningfulPlan(
            container.tokenPayload("id_token")?.string("plan_type", "planType").orEmpty(),
            container.tokenPayload("idToken")?.string("plan_type", "planType").orEmpty(),
        )
    }
    val codexPlan = if (provider == "codex") {
        firstMeaningfulPlan(
            string("plan_type", "planType"),
            tokenPlans.firstOrNull().orEmpty(),
            metadata?.string("plan_type", "planType").orEmpty(),
            tokenPlans.getOrNull(1).orEmpty(),
            attributes?.string("plan_type", "planType").orEmpty(),
            tokenPlans.getOrNull(2).orEmpty(),
        )
    } else ""
    if (codexPlan.isKnownPlan()) return codexPlan

    val tokenPlan = firstMeaningfulPlan(*tokenPlans.toTypedArray())
    if (tokenPlan.isKnownPlan()) return tokenPlan

    val directPlan = firstMeaningfulPlan(
        string("plan_type", "planType"),
        metadata?.string("plan_type", "planType").orEmpty(),
        attributes?.string("plan_type", "planType").orEmpty(),
        string("tier"),
        string("tierName"),
        string("tierId"),
        string("subscriptionType"),
        string("accountType"),
    )
    if (directPlan.isKnownPlan()) return directPlan

    val subscriptionPlan = firstMeaningfulPlan(
        get("subscription").subscriptionPlan(),
        metadata?.get("subscription").subscriptionPlan(),
        attributes?.get("subscription").subscriptionPlan(),
    )
    return firstNonBlank(subscriptionPlan, tokenPlan, directPlan, codexPlan)
}

private fun JsonElement?.subscriptionPlan(): String = when (this) {
    is JsonObject -> firstMeaningfulPlan(string("plan"), string("tierName"), string("tierId"))
    else -> this?.primitiveContent()?.trim().orEmpty()
}

private fun firstMeaningfulPlan(vararg values: String): String {
    val present = values.map(String::trim).filter(String::isNotBlank)
    return present.firstOrNull { it.isKnownPlan() } ?: present.firstOrNull().orEmpty()
}

private fun String.isKnownPlan(): Boolean = isNotBlank() && !equals("unknown", ignoreCase = true)

fun planDisplayLabel(provider: String, rawPlanType: String): String {
    val raw = rawPlanType.trim().replace(Regex("\\s+"), " ")
    val normalized = raw.lowercase()
    if (normalized.isBlank() || normalized == "unknown") return "未知"
    val normalizedProvider = normalizeProvider(provider)
    val known = when (normalizedProvider) {
        "codex" -> CODEX_PLAN_LABELS[normalized]
        "claude" -> CLAUDE_PLAN_LABELS[normalized]
        "antigravity" -> ANTIGRAVITY_PLAN_LABELS[normalized]
        else -> null
    }
    return known ?: raw.take(32)
}

private val CODEX_PLAN_LABELS = mapOf(
    "free" to "Free",
    "go" to "Go",
    "plus" to "Plus",
    "prolite" to "Pro 5x",
    "pro-lite" to "Pro 5x",
    "pro_lite" to "Pro 5x",
    "pro_5x" to "Pro 5x",
    "pro" to "Pro 20x",
    "pro_20x" to "Pro 20x",
    "team" to "Team",
    "self_serve_business_prolite" to "Business 5x",
    "business_premium_5x" to "Business 5x",
    "self_serve_business_usage_based" to "Business PAYG",
    "business_usage_based" to "Business PAYG",
    "business" to "Business",
    "ent26" to "Enterprise",
    "enterprise" to "Enterprise",
    "hc" to "Enterprise",
    "enterprise_cbp_automation" to "Ent. Auto",
    "enterprise_automation" to "Ent. Auto",
    "enterprise_cbp_usage_based" to "Ent. PAYG",
    "enterprise_usage_based" to "Ent. PAYG",
    "edu" to "Edu",
    "education" to "Edu",
    "edu_plus" to "Edu Plus",
    "edu_pro" to "Edu Pro",
)

private val CLAUDE_PLAN_LABELS = mapOf(
    "plan_free" to "Free",
    "free" to "Free",
    "plan_pro" to "Pro",
    "pro" to "Pro",
    "plan_max" to "Max",
    "max" to "Max",
    "plan_max5" to "Max 5x",
    "max_5x" to "Max 5x",
    "plan_max20" to "Max 20x",
    "max_20x" to "Max 20x",
    "plan_team" to "Team",
    "team" to "Team",
)

private val ANTIGRAVITY_PLAN_LABELS = mapOf(
    "free" to "Free",
    "pro" to "Pro",
    "ultra" to "Ultra",
    "ultra-lite" to "Ultra Lite",
    "ultra_lite" to "Ultra Lite",
)

private val genericAccountIdKeys = arrayOf(
    "account_id", "accountId", "chatgpt_account_id", "chatgptAccountId",
    "project_id", "projectId", "gemini_virtual_project", "geminiVirtualProject", "sub",
)

private val codexAccountIdKeys = arrayOf(
    "chatgpt_account_id", "chatgptAccountId", "account_id", "accountId",
)

private fun JsonObject.recursiveGenericAccountId(depth: Int = 0): String {
    string(*genericAccountIdKeys).takeIf(String::isNotBlank)?.let { return it }
    if (depth >= 4) return ""
    for (key in arrayOf("id_token", "idToken", "metadata", "attributes")) {
        val child = get(key) as? JsonObject ?: continue
        child.recursiveGenericAccountId(depth + 1).takeIf(String::isNotBlank)?.let { return it }
    }
    return ""
}

private fun JsonObject.codexAccountId(): String {
    string(*codexAccountIdKeys).takeIf(String::isNotBlank)?.let { return it }
    val containers = listOfNotNull(this, get("metadata") as? JsonObject, get("attributes") as? JsonObject)
    containers.forEach { container ->
        container.string(*codexAccountIdKeys).takeIf(String::isNotBlank)?.let { return it }
    }
    containers.forEach { container ->
        container.tokenPayload("id_token")?.string(*codexAccountIdKeys)
            ?.takeIf(String::isNotBlank)?.let { return it }
        container.tokenPayload("idToken")?.string(*codexAccountIdKeys)
            ?.takeIf(String::isNotBlank)?.let { return it }
    }
    return ""
}

private fun JsonObject.tokenPayload(key: String): JsonObject? {
    val value = get(key) ?: return null
    if (value is JsonObject) return value
    val raw = value.primitiveContent()?.trim().orEmpty()
    if (raw.isBlank()) return null
    runCatching { Json.parseToJsonElement(raw) as? JsonObject }.getOrNull()?.let { return it }
    val payload = raw.split('.').getOrNull(1) ?: return null
    return runCatching {
        val padded = payload + "=".repeat((4 - payload.length % 4) % 4)
        Json.parseToJsonElement(String(Base64.getUrlDecoder().decode(padded))) as? JsonObject
    }.getOrNull()
}

private fun normalizeProvider(value: String): String = when (
    val normalized = value.trim().lowercase().replace('_', '-')
) {
    "x-ai", "grok" -> "xai"
    else -> normalized
}

private fun firstNonBlank(vararg values: String): String =
    values.firstOrNull(String::isNotBlank).orEmpty()

private fun JsonObject.string(vararg keys: String): String = keys.firstNotNullOfOrNull { key ->
    get(key)?.primitiveContent()
}?.trim().orEmpty()

private fun JsonObject.boolean(vararg keys: String): Boolean = keys.firstNotNullOfOrNull { key ->
    get(key)?.let { element ->
        runCatching { element.jsonPrimitive.booleanOrNull }.getOrNull()
            ?: element.primitiveContent()?.trim()?.let { it == "1" || it.equals("true", ignoreCase = true) }
    }
} ?: false

private fun JsonElement.primitiveContent(): String? = runCatching { jsonPrimitive.contentOrNull }.getOrNull()

private fun String.sha256(): String = MessageDigest.getInstance("SHA-256")
    .digest(toByteArray()).joinToString("") { "%02x".format(it) }
