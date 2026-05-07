package com.example.doh_approvedherbalplantidentifcation

import android.content.Context
import android.graphics.BitmapFactory
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import com.greenbuddy.doh_approved_herb_identifier.R

class HerbAdapter(context: Context, private val items: MutableList<HerbModel>) :
    ArrayAdapter<HerbModel>(context, 0, items) {

    // Initialize your helper once
    private val dbHelper = SQLiteHelper(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_herb, parent, false)

        val herb = getItem(position)

        val deleteBtn = view.findViewById<ImageButton>(R.id.singleDeleteBtn)
        val herbImage = view.findViewById<ImageView>(R.id.herb_image)
        val herbLabel = view.findViewById<TextView>(R.id.herb_label)
        val herbConfi = view.findViewById<TextView>(R.id.herb_confi)
        val herbDef = view.findViewById<TextView>(R.id.herb_description)
        val herbS = view.findViewById<TextView>(R.id.herb_safe)

        herb?.let { currentHerb ->
            // Decode image (handling the case where image might be empty from your placeholder logic)
            if (currentHerb.herbalimage.isNotEmpty()) {
                val bitmap = BitmapFactory.decodeByteArray(currentHerb.herbalimage, 0, currentHerb.herbalimage.size)
                herbImage.setImageBitmap(bitmap)
            }

            herbLabel.text = currentHerb.herbalname
            herbConfi.text = String.format("%.2f%%", currentHerb.herballevel * 100)
            herbDef.text = currentHerb.herbaldescription
            herbS.text = currentHerb.herbalsafetywarn

            // --- DELETE BUTTON LOGIC ---
            deleteBtn.setOnClickListener {
                // 1. Delete from SQLite using the ID from your model
                val success = dbHelper.deleteHerb(currentHerb.id)

                if (success) {
                    // 2. Remove from the local list that the Adapter is using
                    items.removeAt(position)

                    // 3. Refresh the UI
                    notifyDataSetChanged()

                    Toast.makeText(context, "Deleted ${currentHerb.herbalname}", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(context, "Failed to delete from database", Toast.LENGTH_SHORT).show()
                }
            }
        }

        return view
    }
}