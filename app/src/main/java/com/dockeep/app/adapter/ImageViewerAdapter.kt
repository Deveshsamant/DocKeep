package com.dockeep.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.dockeep.app.R
import com.dockeep.app.database.DocumentImage
import java.io.File

class ImageViewerAdapter(
    private val images: List<DocumentImage>
) : RecyclerView.Adapter<ImageViewerAdapter.ImageViewHolder>() {

    class ImageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val imageView: ImageView = itemView.findViewById(R.id.imageView)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ImageViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_image_viewer, parent, false)
        return ImageViewHolder(view)
    }

    override fun onBindViewHolder(holder: ImageViewHolder, position: Int) {
        val image = images[position]
        val imageFile = File(image.imagePath)
        
        if (imageFile.exists()) {
            Glide.with(holder.imageView.context)
                .load(imageFile)
                // The editor rewrites the file in place, so the path alone is
                // not a safe cache key; without this the viewer would keep
                // showing the pre-edit image.
                .signature(com.bumptech.glide.signature.ObjectKey(imageFile.lastModified()))
                .fitCenter()
                .into(holder.imageView)
        } else {
            // Show placeholder if image file doesn't exist
            holder.imageView.setImageResource(R.drawable.ic_document_placeholder)
        }
    }

    override fun getItemCount(): Int = images.size
}