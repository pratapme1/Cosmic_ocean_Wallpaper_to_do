package com.cosmicocean.utils

import android.content.Context
import com.cosmicocean.model.ParsedTaskResult
import java.time.Clock
import kotlin.math.max

class HybridTaskParser(
    context: Context,
    private val clock: Clock = Clock.systemDefaultZone()
) {
    private val correctionStore = ParsingCorrectionStore(context)
    private val classifier = LocalIntentClassifier(correctionStore)

    fun parse(input: String?): ParsedTaskResult {
        val base = LocalTaskParser.parse(input, clock)
        val safeInput = input?.trim().orEmpty()
        if (safeInput.isBlank()) return base

        val tokens = classifier.tokenize(safeInput)
        if (tokens.isNotEmpty()) {
            val explicitTags = base.contextTags?.map { it.removePrefix("@").lowercase() } ?: emptyList()
            if (explicitTags.isNotEmpty()) {
                correctionStore.recordContext(tokens, explicitTags.first())
            }
        }

        val contextPrediction = classifier.predictContext(safeInput)
        val priorityPrediction = classifier.predictPriority(safeInput)
        val energyPrediction = classifier.predictEnergy(safeInput)

        val contextTags = when {
            !base.contextTags.isNullOrEmpty() -> base.contextTags
            contextPrediction != null && isContextConfident(contextPrediction) -> listOf("@${contextPrediction.label}")
            else -> emptyList()
        }

        val priority = if (base.priority == 2 && priorityPrediction != null && isPriorityConfident(priorityPrediction)) {
            when (priorityPrediction.label) {
                "high" -> 1
                "low" -> 3
                else -> 2
            }
        } else {
            base.priority
        }

        val energyLevel = when {
            base.energyLevel != null && base.energyLevel != "medium" -> base.energyLevel
            energyPrediction != null && isEnergyConfident(energyPrediction) -> energyPrediction.label
            else -> base.energyLevel
        }

        val category = base.category ?: contextPrediction?.takeIf { isContextConfident(it) }?.let {
            when (it.label) {
                "work" -> "work"
                "home", "grocery", "commute" -> "personal"
                "gym", "health" -> "health"
                else -> null
            }
        }

        val confidence = maxOf(
            base.confidence,
            contextPrediction?.confidence ?: 0.0,
            priorityPrediction?.confidence ?: 0.0,
            energyPrediction?.confidence ?: 0.0
        )

        return base.copy(
            priority = priority,
            category = category,
            energyLevel = energyLevel,
            contextTags = contextTags,
            confidence = confidence,
            source = "local_hybrid"
        )
    }

    private fun isContextConfident(prediction: IntentPrediction): Boolean {
        return prediction.rawScore >= 1.1 && prediction.rawMargin >= 0.4
    }

    private fun isPriorityConfident(prediction: IntentPrediction): Boolean {
        return prediction.rawScore >= 1.1 && prediction.rawMargin >= 0.3
    }

    private fun isEnergyConfident(prediction: IntentPrediction): Boolean {
        return prediction.rawScore >= 1.0 && prediction.rawMargin >= 0.2
    }
}
