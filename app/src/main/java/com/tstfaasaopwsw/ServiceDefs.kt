package com.tstfaasaopwsw

/**
 * User-editable service definitions, persisted in [Store]. Seeded once from
 * [Config.catalog] so every service — built-in or added later — can be renamed, have its
 * package or domain changed, or be deleted (unless its app is installed). Serialized as
 * "id␟label␟pkg,pkg␟domain" rows; an empty domain means none.
 */
data class ServiceDef(
    val id: String,
    val label: String,
    val packages: List<String>,
    val domain: String?,
)

private const val K_SERVICES = "services"
private const val SEP = "\u001F"

fun Store.serviceDefs(): List<ServiceDef> {
    val raw = sp.getStringSet(K_SERVICES, null)
    if (raw == null) { // first run: seed from the catalog
        val seed = Config.catalog.map { ServiceDef(it.domain ?: it.packages.first(), it.label, it.packages, it.domain) }
        saveServiceDefs(seed)
        return seed
    }
    return raw.mapNotNull { it.toServiceDef() }
}

fun Store.upsertService(def: ServiceDef) =
    saveServiceDefs(serviceDefs().filterNot { it.id == def.id } + def)

fun Store.removeService(id: String) =
    saveServiceDefs(serviceDefs().filterNot { it.id == id })

private fun Store.saveServiceDefs(defs: List<ServiceDef>) =
    sp.edit().putStringSet(K_SERVICES, defs.map { it.serialize() }.toSet()).apply()

private fun ServiceDef.serialize() =
    listOf(id, label, packages.joinToString(","), domain ?: "").joinToString(SEP)

private fun String.toServiceDef(): ServiceDef? {
    val p = split(SEP)
    if (p.size != 4) return null
    val packages = p[2].split(",").map { it.trim() }.filter { it.isNotEmpty() }
    return ServiceDef(p[0], p[1], packages, p[3].ifEmpty { null })
}
