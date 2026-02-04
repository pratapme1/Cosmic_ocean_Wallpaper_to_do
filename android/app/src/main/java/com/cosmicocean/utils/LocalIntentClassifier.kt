package com.cosmicocean.utils

import kotlin.math.exp
import kotlin.math.max

data class IntentPrediction(
    val label: String,
    val confidence: Double,
    val scores: Map<String, Double>,
    val rawScores: Map<String, Double>,
    val rawScore: Double,
    val rawMargin: Double
)

class LocalIntentClassifier(
    private val correctionStore: ParsingCorrectionStore? = null
) {
    private val stopwords = setOf(
        "the", "a", "an", "to", "for", "and", "or", "of", "in", "on", "at", "by",
        "with", "from", "is", "it", "this", "that", "my", "your", "our", "me",
        "we", "you", "i", "today", "tomorrow", "tonight", "as", "soon", "later"
    )

    private val contextWeights = mapOf(
        "work" to mapOf(
            "email" to 1.4f, "meeting" to 1.6f, "client" to 1.6f, "report" to 1.3f,
            "review" to 1.2f, "proposal" to 1.4f, "project" to 1.2f, "deadline" to 1.3f,
            "jira" to 1.2f, "slack" to 1.1f, "doc" to 1.0f, "presentation" to 1.4f,
            "standup" to 1.3f, "retro" to 1.2f, "sprint" to 1.2f, "pto" to 1.1f,
            "invoice" to 1.2f, "expense" to 1.2f, "budget" to 1.2f, "calendar" to 1.0f,
            "office" to 1.0f, "pr" to 1.1f, "run_report" to 1.4f
        ),
        "home" to mapOf(
            "laundry" to 1.5f, "dishes" to 1.4f, "clean" to 1.2f, "cook" to 1.3f,
            "kids" to 1.1f, "family" to 1.1f, "yard" to 1.1f, "home" to 1.2f,
            "mail" to 1.0f, "repair" to 1.1f, "organize" to 1.0f, "school" to 1.1f,
            "trash" to 1.2f
        ),
        "grocery" to mapOf(
            "grocery" to 1.7f, "groceries" to 1.7f, "store" to 1.2f, "market" to 1.2f,
            "buy" to 0.9f, "milk" to 1.1f, "eggs" to 1.1f, "bread" to 1.1f,
            "vegetables" to 1.2f, "fruit" to 1.1f, "snacks" to 1.0f, "pharmacy" to 1.1f
        ),
        "gym" to mapOf(
            "gym" to 1.7f, "workout" to 1.5f, "run" to 1.3f, "lift" to 1.3f,
            "exercise" to 1.4f, "training" to 1.3f, "yoga" to 1.2f, "cardio" to 1.2f,
            "5k" to 1.2f
        ),
        "commute" to mapOf(
            "commute" to 1.6f, "train" to 1.2f, "bus" to 1.2f, "drive" to 1.1f,
            "subway" to 1.2f, "ride" to 1.0f, "traffic" to 1.1f, "station" to 1.0f,
            "office" to 1.0f, "work" to 0.8f
        ),
        "health" to mapOf(
            "doctor" to 1.4f, "dentist" to 1.4f, "appointment" to 1.2f, "pharmacy" to 1.2f,
            "meds" to 1.2f, "medicine" to 1.2f, "checkup" to 1.2f, "clinic" to 1.1f,
            "hospital" to 1.2f
        )
    )

    private val priorityWeights = mapOf(
        "high" to mapOf(
            "urgent" to 2.0f, "asap" to 1.9f, "critical" to 2.0f, "now" to 1.4f,
            "deadline" to 1.5f, "overdue" to 1.7f, "today" to 1.2f, "tonight" to 1.2f,
            "right_now" to 1.8f
        ),
        "medium" to mapOf(
            "soon" to 1.2f, "tomorrow" to 1.1f, "week" to 1.0f, "next_week" to 1.1f,
            "follow_up" to 1.0f, "plan" to 0.9f
        ),
        "low" to mapOf(
            "later" to 1.5f, "someday" to 1.7f, "whenever" to 1.4f, "optional" to 1.2f,
            "maybe" to 1.1f, "nice_to_have" to 1.4f
        )
    )

    private val energyWeights = mapOf(
        "high" to mapOf(
            "deep" to 1.4f, "write" to 1.2f, "design" to 1.3f, "workout" to 1.2f,
            "presentation" to 1.3f, "brainstorm" to 1.2f, "build" to 1.1f
        ),
        "low" to mapOf(
            "email" to 1.0f, "call" to 0.9f, "review" to 1.0f, "read" to 0.9f,
            "pay" to 0.9f, "schedule" to 0.9f, "check" to 0.8f
        ),
        "medium" to mapOf(
            "plan" to 1.0f, "organize" to 1.0f, "update" to 0.9f, "prep" to 1.0f
        )
    )

    fun tokenize(input: String): List<String> {
        val normalized = input.lowercase()
            .replace(Regex("[^a-z0-9@ ]"), " ")
            .replace(Regex("\\s+"), " ")
            .trim()
        if (normalized.isBlank()) return emptyList()

        val tokens = normalized.split(" ").filter { it.isNotBlank() && it !in stopwords }
        if (tokens.isEmpty()) return emptyList()

        val bigrams = tokens.zipWithNext { a, b -> "${a}_${b}" }
        val trigrams = if (tokens.size >= 3) tokens.windowed(3).map { it.joinToString("_") } else emptyList()
        return tokens + bigrams + trigrams
    }

    fun predictContext(input: String): IntentPrediction? {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) return null
        val boosts = correctionStore?.getContextBoosts(tokens) ?: emptyMap()
        val scores = scoreLabels(contextWeights, tokens, boosts)
        return bestPrediction(scores)
    }

    fun predictPriority(input: String): IntentPrediction? {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) return null
        val scores = scoreLabels(priorityWeights, tokens, emptyMap())
        return bestPrediction(scores)
    }

    fun predictEnergy(input: String): IntentPrediction? {
        val tokens = tokenize(input)
        if (tokens.isEmpty()) return null
        val scores = scoreLabels(energyWeights, tokens, emptyMap())
        return bestPrediction(scores)
    }

    private fun scoreLabels(
        weights: Map<String, Map<String, Float>>,
        tokens: List<String>,
        boosts: Map<String, Float>
    ): Map<String, Double> {
        val scores = mutableMapOf<String, Double>()
        weights.forEach { (label, tokenWeights) ->
            var score = 0.0
            tokens.forEach { token ->
                val weight = tokenWeights[token]
                if (weight != null) {
                    score += weight.toDouble()
                }
            }
            score += (boosts[label] ?: 0f).toDouble()
            scores[label] = score
        }
        return scores
    }

    private fun bestPrediction(scores: Map<String, Double>): IntentPrediction? {
        if (scores.isEmpty()) return null
        val maxScore = scores.values.maxOrNull() ?: 0.0
        if (maxScore <= 0.0) return null

        val sorted = scores.entries.sortedByDescending { it.value }
        val bestLabel = sorted.first().key
        val bestRaw = sorted.first().value
        val secondRaw = sorted.getOrNull(1)?.value ?: 0.0
        val rawMargin = bestRaw - secondRaw

        val expScores = scores.mapValues { exp(it.value - maxScore) }
        val total = expScores.values.sum().coerceAtLeast(1e-6)
        val probs = expScores.mapValues { it.value / total }

        val confidence = probs[bestLabel] ?: 0.0
        return IntentPrediction(
            label = bestLabel,
            confidence = confidence,
            scores = probs,
            rawScores = scores,
            rawScore = bestRaw,
            rawMargin = rawMargin
        )
    }
}
