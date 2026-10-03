package org.sakshi.app

import android.app.Application
import org.sakshi.app.observation.ObservationLifecycle
import org.sakshi.acquisition.projection.ProjectionCaptureRuntime
import org.sakshi.acquisition.projection.ProjectionRuntimeOwner

class SakshiApplication : Application(), ProjectionRuntimeOwner {
    lateinit var container: AppContainer
        private set
    lateinit var observationLifecycle: ObservationLifecycle
        private set
    override val projectionCaptureRuntime: ProjectionCaptureRuntime
        get() = container.projectionCapture

    override fun onCreate() {
        super.onCreate()
        container = AppContainer.create(this)
        observationLifecycle = ObservationLifecycle(this, container.session)
    }
}
