package com.noctra.app.ui.companion

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.DialogFragment
import com.noctra.app.R
import com.noctra.app.databinding.DialogStreakNoticeBinding
import com.noctra.app.ui.companion.CompanionViewModel.CompanionNotice

class StreakNoticeDialogFragment : DialogFragment() {

    private var _binding: DialogStreakNoticeBinding? = null
    private val binding get() = _binding!!

    private var onDismissListener: (() -> Unit)? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = DialogStreakNoticeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val noticeType = arguments?.getString(ARG_NOTICE_TYPE)?.let { 
            CompanionNotice.valueOf(it) 
        } ?: CompanionNotice.WARNING

        setupContent(noticeType)

        binding.btnOk.setOnClickListener {
            dismiss()
        }
    }

    private fun setupContent(type: CompanionNotice) {
        when (type) {
            CompanionNotice.RESTORED -> {
                binding.tvNoticeTitle.text = "Streak Restored!"
                binding.tvNoticeDescription.text = "Late Data has been found and your streak has been restored!"
                binding.ivShleepyMascot.setImageResource(R.drawable.ic_shleepy_happy)
            }
            CompanionNotice.LOST -> {
                binding.tvNoticeTitle.text = "Streak Broken"
                binding.tvNoticeDescription.text = "Oh no! Your streak got broken. Let's start a new one tonight!"
                binding.ivShleepyMascot.setImageResource(R.drawable.ic_shleepy_sad)
            }
            CompanionNotice.WARNING -> {
                binding.tvNoticeTitle.text = "Streak Safe"
                binding.tvNoticeDescription.text = "Phew! You missed your routine, but I've kept your streak safe. Don't miss tonight!"
                binding.ivShleepyMascot.setImageResource(R.drawable.ic_shleepy_relieved)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        dialog?.window?.setLayout(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
        dialog?.window?.setBackgroundDrawableResource(android.R.color.transparent)
    }

    fun setOnDismissCallback(listener: () -> Unit) {
        this.onDismissListener = listener
    }

    override fun onDismiss(dialog: android.content.DialogInterface) {
        super.onDismiss(dialog)
        onDismissListener?.invoke()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        private const val ARG_NOTICE_TYPE = "notice_type"

        fun newInstance(type: CompanionNotice): StreakNoticeDialogFragment {
            return StreakNoticeDialogFragment().apply {
                arguments = Bundle().apply {
                    putString(ARG_NOTICE_TYPE, type.name)
                }
            }
        }
    }
}
