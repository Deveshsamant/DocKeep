package com.dockeep.app

import android.os.Bundle
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.viewpager2.widget.ViewPager2
import com.dockeep.app.adapter.ImageViewerAdapter
import com.dockeep.app.database.DocumentImage

class ImageViewerActivity : AppCompatActivity() {
    
    private lateinit var viewPager: ViewPager2
    private lateinit var documentNameTextView: TextView
    private lateinit var imageCounterTextView: TextView
    private lateinit var closeButton: ImageButton
    private lateinit var adapter: ImageViewerAdapter
    private lateinit var images: List<DocumentImage>
    private var initialPosition: Int = 0
    private lateinit var documentName: String
    
    companion object {
        const val EXTRA_IMAGES = "extra_images"
        const val EXTRA_INITIAL_POSITION = "extra_initial_position"
        const val EXTRA_DOCUMENT_NAME = "extra_document_name"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_image_viewer)
        
        // Hide status bar for fullscreen experience
        window.setFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN,
            WindowManager.LayoutParams.FLAG_FULLSCREEN
        )
        
        // Get data from intent
        images = intent.getSerializableExtra(EXTRA_IMAGES) as? List<DocumentImage> ?: emptyList()
        initialPosition = intent.getIntExtra(EXTRA_INITIAL_POSITION, 0)
        documentName = intent.getStringExtra(EXTRA_DOCUMENT_NAME) ?: "Document"
        
        initViews()
        setupViewPager()
        updateCounter()
    }
    
    private fun initViews() {
        viewPager = findViewById(R.id.viewPager)
        documentNameTextView = findViewById(R.id.documentName)
        imageCounterTextView = findViewById(R.id.imageCounter)
        closeButton = findViewById(R.id.closeButton)
        
        documentNameTextView.text = documentName
        
        closeButton.setOnClickListener {
            finish()
        }
    }
    
    private fun setupViewPager() {
        adapter = ImageViewerAdapter(images)
        viewPager.adapter = adapter
        viewPager.currentItem = initialPosition
        
        viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)
                updateCounter()
            }
        })
    }
    
    private fun updateCounter() {
        val currentPosition = viewPager.currentItem + 1
        imageCounterTextView.text = "$currentPosition/${images.size}"
    }
}