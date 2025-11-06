package com.dockeep.app.adapter

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.TextView
import com.dockeep.app.database.Document

class DocumentAutocompleteAdapter(
    context: Context,
    private val resource: Int,
    private val allDocuments: List<Document>,
    private val commonDocumentNames: List<String>
) : ArrayAdapter<String>(context, resource) {

    private val filteredItems = mutableListOf<String>()
    private val documentNames = allDocuments.map { it.name }

    init {
        // Combine common document names with existing document names
        val allSuggestions = mutableSetOf<String>()
        allSuggestions.addAll(commonDocumentNames)
        allSuggestions.addAll(documentNames)
        filteredItems.addAll(allSuggestions)
        notifyDataSetChanged()
    }

    override fun getCount(): Int {
        return filteredItems.size
    }

    override fun getItem(position: Int): String? {
        return if (position >= 0 && position < filteredItems.size) {
            filteredItems[position]
        } else {
            null
        }
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(resource, parent, false)
        val textView = view.findViewById<TextView>(android.R.id.text1)
        
        val item = getItem(position)
        if (item != null) {
            textView.text = item
            textView.textSize = 16f
            
            // Highlight items that start with the search term
            val constraint = (context as? androidx.appcompat.app.AppCompatActivity)?.findViewById<androidx.appcompat.widget.AppCompatAutoCompleteTextView>(com.dockeep.app.R.id.documentNameEditText)?.text?.toString()
            if (!constraint.isNullOrEmpty() && item.lowercase().startsWith(constraint.lowercase())) {
                textView.setTextColor(context.getColor(com.dockeep.app.R.color.colorPrimary))
                textView.setAllCaps(false)
            } else {
                textView.setTextColor(context.getColor(android.R.color.black))
                textView.setAllCaps(false)
            }
        }
        
        return view
    }

    override fun getFilter(): Filter {
        return object : Filter() {
            override fun performFiltering(constraint: CharSequence?): FilterResults {
                val filterResults = FilterResults()
                val tempList = mutableListOf<String>()

                if (constraint.isNullOrEmpty()) {
                    // Show popular suggestions when no text is entered
                    val popularDocuments = listOf(
                        "Aadhaar Card", "PAN Card", "Voter ID", "Passport", 
                        "Driving License", "10th Marksheet", "12th Marksheet",
                        "Bank Passbook", "Insurance Policy", "Medical Records"
                    )
                    tempList.addAll(popularDocuments)
                } else {
                    val searchString = constraint.toString().lowercase()
                    
                    // Get all matching documents
                    val allMatches = mutableListOf<String>()
                    allMatches.addAll(commonDocumentNames.filter { 
                        it.lowercase().contains(searchString) 
                    })
                    allMatches.addAll(documentNames.filter { 
                        it.lowercase().contains(searchString) 
                    })
                    
                    // Prioritize documents that START with the search string
                    val startsWithMatches = allMatches.filter { 
                        it.lowercase().startsWith(searchString) 
                    }.take(20)
                    
                    // Then add other matches
                    val containsMatches = allMatches.filter { 
                        !it.lowercase().startsWith(searchString) 
                    }.take(10)
                    
                    // Combine the lists with priority given to startswith matches
                    tempList.addAll(startsWithMatches)
                    tempList.addAll(containsMatches)
                }

                filterResults.values = tempList
                filterResults.count = tempList.size
                return filterResults
            }

            @Suppress("UNCHECKED_CAST")
            override fun publishResults(constraint: CharSequence?, results: FilterResults?) {
                filteredItems.clear()
                if (results != null && results.count > 0) {
                    filteredItems.addAll(results.values as List<String>)
                }
                notifyDataSetChanged()
            }
        }
    }
}