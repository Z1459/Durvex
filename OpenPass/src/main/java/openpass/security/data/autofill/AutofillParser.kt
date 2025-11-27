package openpass.security.data.autofill

import android.app.assist.AssistStructure
import android.text.InputType
import android.util.Log
import android.view.View
import android.view.autofill.AutofillId
import kotlin.math.pow
import kotlin.math.sqrt

data class AutofillScreenData(
    val usernameId: AutofillId? = null,
    val passwordId: AutofillId? = null
)

// Data class to hold collected info about views on the screen
private data class ViewInfo(
    val id: AutofillId,
    val node: AssistStructure.ViewNode,
    val centerX: Float,
    val centerY: Float
)

object AutofillParser {
    fun parse(structure: AssistStructure, webDomain: String?): AutofillScreenData {
        val textLabels = mutableListOf<ViewInfo>()
        val inputFields = mutableListOf<ViewInfo>()

        // --- Pass 1: Traverse the hierarchy and collect all relevant views ---
        for (i in 0 until structure.windowNodeCount) {
            findViews(structure.getWindowNodeAt(i).rootViewNode) { node ->
                val className = node.className ?: ""
                val id = node.autofillId ?: return@findViews

                // Calculate center coordinates for distance checking
                val centerX = node.left + (node.width / 2f)
                val centerY = node.top + (node.height / 2f)
                val viewInfo = ViewInfo(id, node, centerX, centerY)

                if (className.contains("EditText")) {
                    inputFields.add(viewInfo)
                } else if (className.contains("TextView")) {
                    textLabels.add(viewInfo)
                }
            }
        }

        var usernameId: AutofillId? = null
        var passwordId: AutofillId? = null

        // --- Pass 2: Analyze input fields and their nearest labels ---
        for (fieldInfo in inputFields) {
            val node = fieldInfo.node
            val hints = node.autofillHints
            val resourceId = node.idEntry?.lowercase() ?: ""
            val hintText = node.hint?.toString()?.lowercase() ?: ""
            val inputType = node.inputType

            // Find the closest text label to this input field
            val closestLabelText = findClosestLabel(fieldInfo, textLabels)
            Log.d("OpenPassAutofill", "Field (resId: '$resourceId') has closest label: '$closestLabelText'")

            // --- Username Heuristics ---
            if (usernameId == null && (
                        hints?.contains(View.AUTOFILL_HINT_USERNAME) == true ||
                                hints?.contains(View.AUTOFILL_HINT_EMAIL_ADDRESS) == true ||
                                resourceId.contains("email") || resourceId.contains("user") ||
                                hintText.contains("email") || hintText.contains("user") ||
                                closestLabelText.contains("email") || closestLabelText.contains("user") ||
                                (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                                        inputType and InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS)
                        )) {
                usernameId = node.autofillId
            }

            // --- Password Heuristics ---
            if (passwordId == null && (
                        hints?.contains(View.AUTOFILL_HINT_PASSWORD) == true ||
                                resourceId.contains("password") ||
                                hintText.contains("password") ||
                                closestLabelText.contains("password") ||
                                (inputType and InputType.TYPE_MASK_CLASS == InputType.TYPE_CLASS_TEXT &&
                                        inputType and InputType.TYPE_TEXT_VARIATION_PASSWORD == InputType.TYPE_TEXT_VARIATION_PASSWORD)
                        )) {
                passwordId = node.autofillId
            }
        }

        // --- Pass 3: Last Resort Guessing Logic ---
        if (usernameId == null && passwordId == null && inputFields.size == 2) {
            Log.d("OpenPassAutofill", "No fields identified. Applying last-resort guessing logic.")
            val sortedFields = inputFields.sortedBy { it.centerY }
            usernameId = sortedFields[0].id
            passwordId = sortedFields[1].id
        }

        return AutofillScreenData(usernameId, passwordId)
    }

    private fun findClosestLabel(field: ViewInfo, labels: List<ViewInfo>): String {
        var closestLabel: ViewInfo? = null
        var minDistance: Double = Double.MAX_VALUE

        for (label in labels) {
            // Basic check: only consider labels to the left of or above the input field
            if (label.centerX < field.centerX || label.centerY < field.centerY) {
                val distance = sqrt((field.centerX - label.centerX).pow(2) + (field.centerY - label.centerY).pow(2))
                if (distance < minDistance) {
                    minDistance = distance.toDouble()
                    closestLabel = label
                }
            }
        }
        return closestLabel?.node?.text?.toString()?.lowercase() ?: ""
    }

    private fun findViews(node: AssistStructure.ViewNode, onFound: (AssistStructure.ViewNode) -> Unit) {
        onFound(node)
        for (i in 0 until node.childCount) {
            findViews(node.getChildAt(i), onFound)
        }
    }
}