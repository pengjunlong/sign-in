package com.example.signin.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.signin.databinding.ActivityMainBinding
import com.example.signin.service.ChinaMobileSignInService

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        binding.btnOpenAccessibility.setOnClickListener {
            openAccessibilitySettings()
        }

        binding.btnLaunchChinaMobile.setOnClickListener {
            launchChinaMobileApp()
        }
    }

    override fun onResume() {
        super.onResume()
        updateStatus()
    }

    /** 更新界面上的服务状态提示 */
    private fun updateStatus() {
        val enabled = isAccessibilityServiceEnabled()
        if (enabled) {
            binding.tvStatus.text = "✅ 无障碍服务已开启\n打开中国移动 App 即可自动签到"
            binding.tvStatus.setBackgroundColor(0xFF4CAF50.toInt())
            binding.btnLaunchChinaMobile.visibility = View.VISIBLE
        } else {
            binding.tvStatus.text = "❌ 无障碍服务未开启\n请点击下方按钮前往设置中开启"
            binding.tvStatus.setBackgroundColor(0xFFF44336.toInt())
            binding.btnLaunchChinaMobile.visibility = View.GONE
        }
    }

    /** 检查无障碍服务是否已启用 */
    private fun isAccessibilityServiceEnabled(): Boolean {
        val expectedService = "$packageName/${ChinaMobileSignInService::class.java.name}"
        val enabledServices = Settings.Secure.getString(
            contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false
        val colonSplitter = TextUtils.SimpleStringSplitter(':')
        colonSplitter.setString(enabledServices)
        while (colonSplitter.hasNext()) {
            if (colonSplitter.next().equals(expectedService, ignoreCase = true)) {
                return true
            }
        }
        return false
    }

    /** 跳转到无障碍设置页面 */
    private fun openAccessibilitySettings() {
        startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
    }

    /** 启动中国移动 App */
    private fun launchChinaMobileApp() {
        val intent = packageManager.getLaunchIntentForPackage(
            "com.greenpoint.android.mc10086.activity"
        )
        if (intent != null) {
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } else {
            binding.tvStatus.text = "⚠️ 未检测到中国移动 App，请先安装"
        }
    }
}

