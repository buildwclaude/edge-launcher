package app.edge.launcher

import android.content.Context
import android.widget.Toast

object SystemActions {
    fun lockScreen(context: Context) {
        Toast.makeText(context, "Turn on the Edge accessibility service to lock by double-tap", Toast.LENGTH_SHORT).show()
    }
}
