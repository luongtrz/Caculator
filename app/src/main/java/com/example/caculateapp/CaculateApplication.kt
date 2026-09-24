package com.example.caculateapp

import android.app.Application

/**
 * Application class for app-wide initialization
 */
class CaculateApplication : Application() {

    companion object {
        lateinit var instance: CaculateApplication
            private set
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
    }
}
