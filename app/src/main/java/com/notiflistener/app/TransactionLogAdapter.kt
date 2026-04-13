package com.notiflistener.app

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.notiflistener.app.data.TransactionLog
import com.notiflistener.app.util.AmountExtractor
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * RecyclerView Adapter untuk menampilkan log transaksi.
 */
class TransactionLogAdapter :
    ListAdapter<TransactionLog, TransactionLogAdapter.ViewHolder>(DiffCallback) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_transaction_log, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvSource: TextView = itemView.findViewById(R.id.tvSource)
        private val tvType: TextView = itemView.findViewById(R.id.tvType)
        private val tvAmount: TextView = itemView.findViewById(R.id.tvAmount)
        private val tvSender: TextView = itemView.findViewById(R.id.tvSender)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvWebhookStatus: TextView = itemView.findViewById(R.id.tvWebhookStatus)

        private val dateFormat = SimpleDateFormat("dd MMM yyyy, HH:mm:ss", Locale("id", "ID"))

        fun bind(log: TransactionLog) {
            // Source badge
            tvSource.text = log.source
            tvSource.setBackgroundResource(R.drawable.badge_background)
            val badgeColor = getBadgeColor(log.source)
            tvSource.background.setTint(badgeColor)

            // Transaction type
            val isIncoming = log.transactionType == "INCOMING"
            tvType.text = if (isIncoming) "MASUK" else if (log.transactionType == "OUTGOING") "KELUAR" else "?"
            tvType.setTextColor(
                itemView.context.getColor(
                    if (isIncoming) R.color.type_incoming else R.color.type_outgoing
                )
            )

            // Amount
            tvAmount.text = AmountExtractor.formatRupiah(log.amount)
            tvAmount.setTextColor(
                itemView.context.getColor(
                    if (isIncoming) R.color.type_incoming else R.color.type_outgoing
                )
            )

            // Sender
            if (!log.senderName.isNullOrBlank()) {
                tvSender.text = "dari ${log.senderName}"
                tvSender.visibility = View.VISIBLE
            } else {
                tvSender.text = log.rawText.take(80)
                tvSender.visibility = View.VISIBLE
            }

            // Timestamp
            tvTimestamp.text = dateFormat.format(Date(log.timestamp))

            // Webhook status
            tvWebhookStatus.text = log.webhookStatus
            val statusColor = when (log.webhookStatus) {
                "SENT" -> R.color.status_sent
                "FAILED" -> R.color.status_failed
                else -> R.color.status_pending
            }
            tvWebhookStatus.setTextColor(itemView.context.getColor(statusColor))
        }

        private fun getBadgeColor(source: String): Int {
            return when (source.uppercase()) {
                "DANA" -> itemView.context.getColor(R.color.badge_dana)
                "BCA" -> itemView.context.getColor(R.color.badge_bca)
                "BRI" -> itemView.context.getColor(R.color.badge_bri)
                "BNI" -> itemView.context.getColor(R.color.badge_bni)
                "MANDIRI" -> itemView.context.getColor(R.color.badge_mandiri)
                else -> itemView.context.getColor(R.color.badge_default)
            }
        }
    }

    object DiffCallback : DiffUtil.ItemCallback<TransactionLog>() {
        override fun areItemsTheSame(oldItem: TransactionLog, newItem: TransactionLog): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: TransactionLog, newItem: TransactionLog): Boolean {
            return oldItem == newItem
        }
    }
}
