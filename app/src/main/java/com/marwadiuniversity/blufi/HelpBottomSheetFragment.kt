package com.marwadiuniversity.blufi

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class HelpBottomSheetFragment : BottomSheetDialogFragment() {

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        // This is where we connect our layout file to the fragment
        return inflater.inflate(R.layout.bottom_sheet_help, container, false)
    }

    companion object {
        // A unique tag for the fragment
        const val TAG = "HelpBottomSheetFragment"

        // A function to create a new instance of this fragment
        fun newInstance(): HelpBottomSheetFragment {
            return HelpBottomSheetFragment()
        }
    }
}