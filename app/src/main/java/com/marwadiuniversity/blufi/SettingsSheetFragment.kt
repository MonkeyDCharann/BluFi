package com.marwadiuniversity.blufi

import android.content.Context
import android.content.Intent
import android.graphics.drawable.GradientDrawable
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.GridView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class SettingsSheetFragment : BottomSheetDialogFragment() {

    // Listener to communicate back to the activity
    interface SettingsListener {
        fun onWallpaperColorSelected(colorResId: Int)
    }

    private var listener: SettingsListener? = null
    private val colors = listOf(
        // Original Colors
        R.color.wallpaper_default, R.color.wallpaper_blush, R.color.wallpaper_mint, R.color.wallpaper_sky,

        R.color.wallpaper_lavender, R.color.wallpaper_sand, R.color.wallpaper_slate, R.color.wallpaper_night,
        // New Light Tones
        R.color.wallpaper_peach, R.color.wallpaper_lilac, R.color.wallpaper_cream, R.color.wallpaper_rose,
        // New Vibrant Tones
        R.color.wallpaper_aqua, R.color.wallpaper_coral, R.color.wallpaper_olive, R.color.wallpaper_gold,
        // New Dark Tones
        R.color.wallpaper_forest, R.color.wallpaper_navy, R.color.wallpaper_maroon, R.color.wallpaper_charcoal,

        R.color.wallpaper_terracotta, R.color.wallpaper_sage, R.color.wallpaper_steel_blue, R.color.wallpaper_mauve,

        R.color.wallpaper_mustard, R.color.wallpaper_teal, R.color.wallpaper_plum, R.color.wallpaper_ruby,
        R.color.wallpaper_graphite, R.color.wallpaper_periwinkle,R.color.wallpaper_crimson, R.color.wallpaper_ivory
    )

    override fun onAttach(context: Context) {
        super.onAttach(context)
        listener = context as? SettingsListener
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View? {
        return inflater.inflate(R.layout.fragment_settings_sheet, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<View>(R.id.layout_wallpaper).setOnClickListener {
            showColorPickerDialog()
        }

        view.findViewById<View>(R.id.layout_help).setOnClickListener {
            HelpBottomSheetFragment.newInstance().show(parentFragmentManager, HelpBottomSheetFragment.TAG)
            dismiss() // Close settings sheet
        }

        view.findViewById<TextView>(R.id.layout_report_bug).setOnClickListener {
            val intent = Intent(activity, ReportBugActivity::class.java)
            startActivity(intent)
            dismiss()
        }

        view.findViewById<TextView>(R.id.layout_terms_policy).setOnClickListener {
            val githubUrl = "https://github.com/MonkeyDCharann/BluFi" // Replace with your GitHub URL
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(githubUrl))
            startActivity(intent)
            dismiss()
        }

        view.findViewById<TextView>(R.id.layout_developed_by).setOnClickListener {
            val linkedInUrl = "https://www.linkedin.com/in/devicharan-dasari-b57468276/" // Replace with your LinkedIn URL
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(linkedInUrl))
            startActivity(intent)
            dismiss()
        }
    }

    private fun showColorPickerDialog() {
        val dialogView = LayoutInflater.from(context).inflate(R.layout.dialog_color_picker, null)
        val colorGrid = dialogView.findViewById<GridView>(R.id.color_grid)

        val colorAdapter = ColorAdapter(requireContext(), colors)
        colorGrid.adapter = colorAdapter

        val dialog = AlertDialog.Builder(requireContext())
            .setTitle("Select a Wallpaper Color")
            .setView(dialogView)
            .setNegativeButton("Cancel", null)
            .create()

        colorGrid.setOnItemClickListener { _, _, position, _ ->
            val selectedColor = colors[position]
            listener?.onWallpaperColorSelected(selectedColor)
            dialog.dismiss()
            dismiss() // Close settings sheet as well
        }

        dialog.show()
    }

    // A simple adapter to display colors in the GridView
    private class ColorAdapter(private val context: Context, private val colors: List<Int>) : BaseAdapter() {
        override fun getCount(): Int = colors.size
        override fun getItem(position: Int): Any = colors[position]
        override fun getItemId(position: Int): Long = position.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.item_color, parent, false)
            val colorView = view.findViewById<View>(R.id.color_view)
            val colorRes = colors[position]
            (colorView.background as? GradientDrawable)?.setColor(ContextCompat.getColor(context, colorRes))
            return view
        }
    }
}