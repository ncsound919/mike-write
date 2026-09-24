package com.example.deterministic

enum class EntityType {
    PERSON,
    PLACE,
    OBJECT,
    EVENT
}

data class EntityRecord(
    val name: String,
    val type: EntityType,
    val mentions: Int = 1
)

/**
 * Agent 3 — Entity Registry
 * Tracks named people, places, objects, and key life events across the manuscript
 * using deterministic title-casing heuristics and known category lists.
 */
object EntityRegistryAgent {

    private val KNOWN_TITLES = listOf(
        "Grandpa", "Grandma", "Dad", "Mom", "Mother", "Father", "Uncle", "Aunt",
        "Brother", "Sister", "Doctor", "Dr.", "Coach", "Nurse", "Therapist", "Professor",
        "Captain", "Pastor", "Reverend", "Officer", "Cousin", "Neighbor", "Friend"
    )
    private val PLACE_KEYWORDS = listOf(
        "hospital", "school", "house", "cabin", "farm", "lake", "ocean", "river", "valley",
        "street", "avenue", "city", "clinic", "rehab", "station", "workshop", "garage",
        "backyard", "kitchen", "porch", "stadium", "church", "camp", "office", "bedroom",
        "living room", "hallway", "attic", "basement", "dock", "harbor", "mountain", "trail"
    )
    private val OBJECT_KEYWORDS = listOf(
        "wheelchair", "cart", "car", "truck", "boat", "bike", "bicycle", "plane",
        "guitar", "piano", "tools", "wood", "watch", "camera", "letter", "photo", "ring"
    )
    private val STOP_WORDS = setOf(
        "The", "A", "An", "In", "On", "At", "To", "For", "With", "By", "My", "Our",
        "His", "Her", "Their", "We", "They", "He", "She", "It", "When", "While", "After",
        "Before", "Then", "So", "And", "But", "If", "Because", "Just", "Also", "Now"
    )

    /**
     * Extracts named entities from a memory passage.
     */
    fun extractEntities(text: String): List<EntityRecord> {
        val found = mutableListOf<EntityRecord>()
        val words = text.split(Regex("\\s+"))

        // 1. Scan for relationship titles & capitalized names
        for (i in words.indices) {
            val raw = words[i].trim(',', '.', '!', '?', ';', ':', '"', '\'')
            if (raw.isBlank()) continue

            if (KNOWN_TITLES.any { it.equals(raw, ignoreCase = true) }) {
                val matchTitle = KNOWN_TITLES.first { it.equals(raw, ignoreCase = true) }
                // Check if followed by a name: "Uncle John"
                val nextWord = words.getOrNull(i + 1)?.trim(',', '.', '!', '?', ';', ':')
                if (nextWord != null && nextWord.isNotEmpty() && nextWord[0].isUpperCase() && !STOP_WORDS.contains(nextWord)) {
                    found.add(EntityRecord("$matchTitle $nextWord", EntityType.PERSON))
                } else {
                    found.add(EntityRecord(matchTitle, EntityType.PERSON))
                }
            } else if (raw.isNotEmpty() && raw[0].isUpperCase() && !STOP_WORDS.contains(raw) && raw.length > 2) {
                // Title-cased person name candidate (e.g. "Tommy", "Sarah", "Dr. Jenkins")
                if (i > 0) { // Not start of sentence
                    found.add(EntityRecord(raw, EntityType.PERSON))
                }
            }
        }

        // 2. Scan for places
        val lowerText = text.lowercase()
        for (placeKw in PLACE_KEYWORDS) {
            if (lowerText.contains(placeKw)) {
                val regex = Regex("(?i)\\b([a-z0-9'-]+\\s+${placeKw}|the\\s+${placeKw}|${placeKw})\\b")
                val match = regex.find(text)
                if (match != null) {
                    found.add(EntityRecord(match.value.trim(), EntityType.PLACE))
                } else {
                    found.add(EntityRecord(placeKw, EntityType.PLACE))
                }
            }
        }

        // 3. Scan for significant objects
        for (objKw in OBJECT_KEYWORDS) {
            if (lowerText.contains(objKw)) {
                val regex = Regex("(?i)\\b([a-z0-9'-]+\\s+${objKw}|the\\s+${objKw}|${objKw})\\b")
                val match = regex.find(text)
                if (match != null) {
                    found.add(EntityRecord(match.value.trim(), EntityType.OBJECT))
                } else {
                    found.add(EntityRecord(objKw, EntityType.OBJECT))
                }
            }
        }

        // Deduplicate
        return found.distinctBy { it.name.lowercase() }
    }
}
