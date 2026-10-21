package com.hossammo.cash

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import com.hossammo.cash.data.security.LockManager
import com.hossammo.cash.ui.AppRoot
import com.hossammo.cash.ui.MainViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * FragmentActivity مطلوبة لـ BiometricPrompt.
 */
@AndroidEntryPoint
class MainActivity : FragmentActivity() {

    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var lockManager: LockManager

    private val processObserver = object : DefaultLifecycleObserver {
        override fun onStop(owner: LifecycleOwner) = lockManager.onBackground()
        override fun onStart(owner: LifecycleOwner) {
            lifecycleScope.launch { lockManager.onForeground() }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        // منع لقطات الشاشة وإخفاء المحتوى في شاشة التطبيقات الحديثة افتراضيًا حتى تُقرأ الإعدادات
        window.setFlags(WindowManager.LayoutParams.FLAG_SECURE, WindowManager.LayoutParams.FLAG_SECURE)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        ProcessLifecycleOwner.get().lifecycle.addObserver(processObserver)
        setContent {
            val prefs by viewModel.prefs.collectAsStateWithLifecycle()
            LaunchedEffect(prefs?.secureScreen) {
                val secure = prefs?.secureScreen ?: true
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
            AppRoot(viewModel)
        }
    }

    override fun onUserInteraction() {
        super.onUserInteraction()
        lockManager.onUserInteraction()
    }

    override fun onDestroy() {
        ProcessLifecycleOwner.get().lifecycle.removeObserver(processObserver)
        super.onDestroy()
    }
}
