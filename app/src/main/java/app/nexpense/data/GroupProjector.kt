package app.nexpense.data

import app.nexpense.ledger.LedgerReducer
import app.nexpense.ledger.LedgerSnapshot
import app.nexpense.nostr.GroupSettings
import app.nexpense.nostr.LedgerEvent
import app.nexpense.nostr.NostrEvent
import app.nexpense.nostr.Payloads
import app.nexpense.nostr.Person
import app.nexpense.nostr.protocolJson

data class GroupState(
    val group: GroupEntity,
    val settings: GroupSettings,
    val people: List<Person>,
    val ledger: LedgerSnapshot,
    val pendingCount: Int,
) {
    val displayName: String get() = settings.name.ifBlank { group.localName }
    val actingPerson: Person? get() = people.firstOrNull { it.personId == group.actingMemberId }
}

object GroupProjector {
    fun project(group: GroupEntity, cached: List<CachedEventEntity>): GroupState {
        val deleted = cached.filter { it.kind == 5 }.flatMap { entity ->
            tags(entity).filter { it.firstOrNull() == "e" }.mapNotNull { it.getOrNull(1) }
        }.toSet()
        val ledgerEvents = cached.filter { it.kind == 78 && it.plaintext != null }.mapNotNull { entity ->
            runCatching { LedgerEvent(entity.event(), Payloads.decodeLedger(entity.plaintext!!)) }.getOrNull()
        }
        val settings = cached.filter { it.kind == 30078 && tag(it, "d") == "nexpense:v1:group" }
            .sortedWith(compareBy({ it.createdAt }, { it.id }))
            .lastOrNull()?.plaintext?.let { runCatching { protocolJson.decodeFromString<GroupSettings>(it) }.getOrNull() }
            ?: GroupSettings(name = group.localName)
        val people = cached.filter { it.kind == 30078 && tag(it, "d")?.startsWith("nexpense:v1:person:") == true }
            .groupBy { tag(it, "d")!! }.values.mapNotNull { versions ->
                versions.maxWithOrNull(compareBy<CachedEventEntity> { it.createdAt }.thenBy { it.id })?.plaintext?.let {
                    runCatching { protocolJson.decodeFromString<Person>(it) }.getOrNull()
                }
            }.filter { it.active }.sortedBy { it.name.lowercase() }
        return GroupState(group, settings, people, LedgerReducer.reduce(ledgerEvents, deleted), cached.count { it.pending })
    }

    private fun tags(entity: CachedEventEntity): List<List<String>> =
        runCatching { protocolJson.decodeFromString<List<List<String>>>(entity.tagsJson) }.getOrDefault(emptyList())

    private fun tag(entity: CachedEventEntity, name: String) =
        tags(entity).firstOrNull { it.firstOrNull() == name }?.getOrNull(1)
}

fun CachedEventEntity.event() = NostrEvent(id, pubkey, createdAt, kind,
    runCatching { protocolJson.decodeFromString<List<List<String>>>(tagsJson) }.getOrDefault(emptyList()), content, sig)
