package com.dockeep.app

import android.animation.AnimatorInflater
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.card.MaterialCardView
import com.google.android.material.button.MaterialButton
import com.google.android.material.floatingactionbutton.FloatingActionButton

class DeveloperDetailsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_developer_details)

        // Remove the developer card click listener - only buttons should be clickable
        val developerCard = findViewById<MaterialCardView>(R.id.developerCard)
        val viewPortfolioButton = findViewById<MaterialButton>(R.id.viewPortfolioButton)

        // Set developer photo
        val developerPhoto = findViewById<ImageView>(R.id.developerPhoto)
        developerPhoto.setImageResource(R.drawable.myphoto)

        // Set up social media buttons
        val githubButton = findViewById<FloatingActionButton>(R.id.githubButton)
        val linkedinButton = findViewById<FloatingActionButton>(R.id.linkedinButton)
        val twitterButton = findViewById<FloatingActionButton>(R.id.twitterButton)
        val instagramButton = findViewById<FloatingActionButton>(R.id.instagramButton)
        val emailButton = findViewById<FloatingActionButton>(R.id.emailButton)

        // Set click listeners for social media buttons
        githubButton.setOnClickListener {
            openUrl("https://github.com/Deveshsamant")
        }

        linkedinButton.setOnClickListener {
            openUrl("https://www.linkedin.com/in/devesh-samant-b78376258/")
        }

        twitterButton.setOnClickListener {
            openUrl("https://x.com/DeveshSama32978")
        }

        instagramButton.setOnClickListener {
            openUrl("https://www.instagram.com/devesh.samant/")
        }

        emailButton.setOnClickListener {
            val emailIntent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_EMAIL, arrayOf("aani64257@gmail.com") as java.io.Serializable)
                putExtra(Intent.EXTRA_SUBJECT, "Contact from DocKeep App" as java.io.Serializable)
            }
            startActivity(Intent.createChooser(emailIntent, "Send Email"))
        }

        // Set click listener to open the portfolio website
        viewPortfolioButton.setOnClickListener {
            openUrl("https://devesh-samant-12.vercel.app/")
        }

        // Apply state list animator to the portfolio button
        val stateListAnimator = AnimatorInflater.loadStateListAnimator(this, R.animator.button_pulse)
        viewPortfolioButton.stateListAnimator = stateListAnimator

        // Set up back button in action bar if available
        supportActionBar?.setDisplayHomeAsUpEnabled(true)
    }

    private fun openUrl(url: String) {
        val intent = Intent(Intent.ACTION_VIEW)
        intent.data = Uri.parse(url)
        startActivity(intent)
    }

    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
}