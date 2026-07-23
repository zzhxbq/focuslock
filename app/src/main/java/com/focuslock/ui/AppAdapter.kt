package com.focuslock.ui

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.focuslock.databinding.ItemAppBinding
import com.focuslock.model.AppInfo

/**
 * 应用列表适配器，支持三种模式：
 * - [Mode.LOCKED] 黑名单：开关表示是否加入锁定目标
 * - [Mode.WHITELIST] 白名单：开关表示是否始终放行
 * - [Mode.USAGE] 使用统计：只读，显示今日时长
 */
class AppAdapter(
    private val mode: Mode,
    private val isChecked: (AppInfo) -> Boolean,
    private val onToggle: (AppInfo, Boolean) -> Unit
) : ListAdapter<AppInfo, AppAdapter.AppVH>(DIFF) {

    enum class Mode { LOCKED, WHITELIST, USAGE }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AppVH {
        val binding = ItemAppBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return AppVH(binding)
    }

    override fun onBindViewHolder(holder: AppVH, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AppVH(private val binding: ItemAppBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(info: AppInfo) {
            binding.ivAppIcon.setImageDrawable(info.icon)
            binding.tvAppName.text = info.label
            binding.tvAppPackage.text = info.packageName

            if (mode == Mode.USAGE) {
                binding.swEnable.visibility = ViewGroup.GONE
                binding.tvAppUsage.visibility = android.view.View.VISIBLE
                binding.tvAppUsage.text = formatDuration(info.usageTodayMs)
            } else {
                binding.tvAppUsage.visibility = android.view.View.GONE
                binding.swEnable.visibility = android.view.View.VISIBLE
                binding.swEnable.setOnCheckedChangeListener(null)
                binding.swEnable.isChecked = isChecked(info)
                binding.swEnable.setOnCheckedChangeListener { _, checked ->
                    onToggle(info, checked)
                }
            }
        }

        private fun formatDuration(millis: Long): String {
            if (millis <= 0) return "0 分钟"
            val totalMin = millis / 60000
            val h = totalMin / 60
            val m = totalMin % 60
            return when {
                h > 0 -> "${h} 小时 ${m} 分钟"
                else -> "${m} 分钟"
            }
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<AppInfo>() {
            override fun areItemsTheSame(o: AppInfo, n: AppInfo) = o.packageName == n.packageName
            override fun areContentsTheSame(o: AppInfo, n: AppInfo) = o == n
        }
    }
}
