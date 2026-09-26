package app.edge.launcher

import android.content.Context
import android.widget.Toast
import app.edge.launcher.service.EdgeAccessibilityService

object SystemActions {
    fun lockScreen(context: Context) {
        if (EdgeAccessibilityService.instance?.lockScreen() != true) {
            Toast.makeText(context, "Turn on the Edge accessibility service to lock by double-tap", Toast.LENGTH_SHORT).show()
        }
    }
}
