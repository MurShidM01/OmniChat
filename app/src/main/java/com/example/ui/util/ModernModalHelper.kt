package com.example.ui.util

import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.R
import com.google.android.material.button.MaterialButton
import com.google.android.material.snackbar.Snackbar

object ModernModalHelper {

    enum class ModalType {
        INFO, SUCCESS, WARNING, DANGER
    }

    fun showModal(
        context: Context,
        title: String,
        message: String,
        type: ModalType = ModalType.INFO,
        positiveButtonText: String = "OK",
        negativeButtonText: String? = null,
        onPositiveClick: (() -> Unit)? = null,
        onNegativeClick: (() -> Unit)? = null
    ) {
        val view = LayoutInflater.from(context).inflate(R.layout.dialog_modern_modal, null)
        val dialog = AlertDialog.Builder(context)
            .setView(view)
            .create()

        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val ivIcon = view.findViewById<ImageView>(R.id.iv_dialog_icon)
        val tvTitle = view.findViewById<TextView>(R.id.tv_dialog_title)
        val tvMessage = view.findViewById<TextView>(R.id.tv_dialog_message)
        val btnPositive = view.findViewById<MaterialButton>(R.id.btn_positive)
        val btnNegative = view.findViewById<MaterialButton>(R.id.btn_negative)

        tvTitle.text = title
        tvMessage.text = message
        btnPositive.text = positiveButtonText

        when (type) {
            ModalType.INFO -> {
                ivIcon.setImageResource(R.drawable.ic_sparkle)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.brand_primary))
                btnPositive.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_primary))
            }
            ModalType.SUCCESS -> {
                ivIcon.setImageResource(R.drawable.ic_check)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.brand_emerald))
                btnPositive.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_emerald))
            }
            ModalType.WARNING -> {
                ivIcon.setImageResource(R.drawable.ic_tune)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.brand_amber))
                btnPositive.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_amber))
            }
            ModalType.DANGER -> {
                ivIcon.setImageResource(R.drawable.ic_delete)
                ivIcon.setColorFilter(ContextCompat.getColor(context, R.color.brand_rose))
                btnPositive.setBackgroundColor(ContextCompat.getColor(context, R.color.brand_rose))
            }
        }

        btnPositive.setOnClickListener {
            dialog.dismiss()
            onPositiveClick?.invoke()
        }

        if (negativeButtonText != null) {
            btnNegative.visibility = View.VISIBLE
            btnNegative.text = negativeButtonText
            btnNegative.setOnClickListener {
                dialog.dismiss()
                onNegativeClick?.invoke()
            }
        } else {
            btnNegative.visibility = View.GONE
        }

        dialog.show()
    }

    fun showSnackbar(view: View, message: String, isSuccess: Boolean = true, duration: Int = Snackbar.LENGTH_SHORT) {
        val snackbar = Snackbar.make(view, message, duration)
        val snackbarView = snackbar.view
        snackbarView.setBackgroundResource(R.drawable.bg_modern_snackbar)
        snackbarView.elevation = 0f
        snackbar.setTextColor(Color.WHITE)
        snackbar.show()
    }
}
