package com.dockeep.app.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.dockeep.app.R
import com.dockeep.app.database.Person
import com.google.android.material.card.MaterialCardView

class PersonAdapter(
    private val onPersonClick: (Person) -> Unit
) : ListAdapter<Person, PersonAdapter.PersonViewHolder>(PersonDiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): PersonViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_person, parent, false)
        return PersonViewHolder(view)
    }

    override fun onBindViewHolder(holder: PersonViewHolder, position: Int) {
        val person = getItem(position)
        holder.bind(person)
    }

    inner class PersonViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val personInitialTextView: TextView = itemView.findViewById(R.id.personInitialTextView)
        private val personNameTextView: TextView = itemView.findViewById(R.id.personNameTextView)
        private val cardView: MaterialCardView = itemView.findViewById(R.id.cardView)

        fun bind(person: Person) {
            personNameTextView.text = person.name

            // Set initial letter
            val initial = if (person.name.isNotEmpty()) {
                person.name.first().uppercaseChar().toString()
            } else {
                itemView.context.getString(R.string.document_initial_placeholder)
            }
            personInitialTextView.text = initial

            // Set click listeners
            cardView.setOnClickListener {
                onPersonClick(person)
            }
        }
    }
}

class PersonDiffCallback : DiffUtil.ItemCallback<Person>() {
    override fun areItemsTheSame(oldItem: Person, newItem: Person): Boolean {
        return oldItem.id == newItem.id
    }

    override fun areContentsTheSame(oldItem: Person, newItem: Person): Boolean {
        return oldItem == newItem
    }
}