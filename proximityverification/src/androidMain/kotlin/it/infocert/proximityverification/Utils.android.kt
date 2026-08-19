@file:Suppress("EXPECT_ACTUAL_CLASSIFIERS_ARE_IN_BETA_WARNING")

package it.infocert.proximityverification

import android.content.Context
import androidx.startup.Initializer
import org.multipaz.context.initializeApplication


actual object MultipazContext {
    actual fun initAppForMultipaz() {
        initializeApplication(ContextInitializer.applicationContext)
    }
}

public class ContextInitializer: Initializer<Context> {
    companion object {
        lateinit var applicationContext: Context
            private set
    }
    override fun create(context: Context): Context {
        applicationContext = context.applicationContext
        return applicationContext
    }

    override fun dependencies(): List<Class<out Initializer<*>>> {
        return emptyList()
    }
}