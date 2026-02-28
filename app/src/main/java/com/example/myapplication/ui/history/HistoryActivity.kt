package com.example.myapplication.ui.history

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.myapplication.R
import com.google.android.material.card.MaterialCardView
import com.google.android.material.textview.MaterialTextView
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class HistoryActivity : AppCompatActivity() {
    
    private val viewModel: HistoryViewModel by viewModels()
    
    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: SessionAdapter
    
    private lateinit var cardStatistics: MaterialCardView
    private lateinit var txtTotalSessions: MaterialTextView
    private lateinit var txtTotalMinutes: MaterialTextView
    private lateinit var txtTotalCalories: MaterialTextView
    private lateinit var txtAvgLevel: MaterialTextView
    
    private lateinit var emptyView: View
    private lateinit var loadingView: View
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_history)
        
        initViews()
        setupRecyclerView()
        observeViewModel()
        
        viewModel.loadSessions()
        viewModel.loadStatistics()
    }
    
    private fun initViews() {
        findViewById<View>(R.id.btnBack).setOnClickListener { finish() }
        
        recyclerView = findViewById(R.id.recyclerView)
        cardStatistics = findViewById(R.id.cardStatistics)
        
        txtTotalSessions = findViewById(R.id.txtTotalSessions)
        txtTotalMinutes = findViewById(R.id.txtTotalMinutes)
        txtTotalCalories = findViewById(R.id.txtTotalCalories)
        txtAvgLevel = findViewById(R.id.txtAvgLevel)
        
        emptyView = findViewById(R.id.emptyView)
        loadingView = findViewById(R.id.loadingView)
    }
    
    private fun setupRecyclerView() {
        adapter = SessionAdapter()
        recyclerView.layoutManager = LinearLayoutManager(this)
        recyclerView.adapter = adapter
    }
    
    private fun observeViewModel() {
        lifecycleScope.launch {
            viewModel.sessionState.collect { state ->
                when (state) {
                    is SessionState.Loading -> {
                        showLoading(true)
                        emptyView.visibility = View.GONE
                    }
                    is SessionState.Success -> {
                        showLoading(false)
                        if (state.sessions.isEmpty()) {
                            emptyView.visibility = View.VISIBLE
                            recyclerView.visibility = View.GONE
                        } else {
                            emptyView.visibility = View.GONE
                            recyclerView.visibility = View.VISIBLE
                            adapter.submitList(state.sessions)
                        }
                    }
                    is SessionState.Error -> {
                        showLoading(false)
                        Toast.makeText(this@HistoryActivity, state.message, Toast.LENGTH_LONG).show()
                    }
                    is SessionState.Idle -> {
                        showLoading(false)
                    }
                }
            }
        }
        
        lifecycleScope.launch {
            viewModel.statisticsState.collect { state ->
                when (state) {
                    is StatisticsState.Success -> {
                        displayStatistics(state.stats)
                    }
                    else -> {}
                }
            }
        }
    }
    
    private fun displayStatistics(stats: SessionStatistics) {
        txtTotalSessions.text = stats.totalSessions.toString()
        txtTotalMinutes.text = "${stats.totalMinutes} phút"
        txtTotalCalories.text = "${stats.totalCalories} cal"
        txtAvgLevel.text = "%.1f".format(stats.avgLevel)
    }
    
    private fun showLoading(show: Boolean) {
        loadingView.visibility = if (show) View.VISIBLE else View.GONE
    }
}

// Session Adapter
class SessionAdapter : RecyclerView.Adapter<SessionAdapter.ViewHolder>() {
    
    private var sessions = listOf<MassageSessionItem>()
    private val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault())
    
    fun submitList(newSessions: List<MassageSessionItem>) {
        sessions = newSessions
        notifyDataSetChanged()
    }
    
    override fun onCreateViewHolder(parent: android.view.ViewGroup, viewType: Int): ViewHolder {
        val view = android.view.LayoutInflater.from(parent.context)
            .inflate(R.layout.item_massage_session, parent, false)
        return ViewHolder(view)
    }
    
    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(sessions[position])
    }
    
    override fun getItemCount() = sessions.size
    
    inner class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val txtDate: MaterialTextView = itemView.findViewById(R.id.txtDate)
        private val txtLevel: MaterialTextView = itemView.findViewById(R.id.txtLevel)
        private val txtDuration: MaterialTextView = itemView.findViewById(R.id.txtDuration)
        private val txtCalories: MaterialTextView = itemView.findViewById(R.id.txtCalories)
        private val iconHeat: View = itemView.findViewById(R.id.iconHeat)
        private val iconRotate: View = itemView.findViewById(R.id.iconRotate)
        
        fun bind(session: MassageSessionItem) {
            txtDate.text = dateFormat.format(Date(session.startedAt))
            txtLevel.text = "Level ${session.level}"
            txtDuration.text = "${session.duration} phút"
            txtCalories.text = "${session.caloriesBurned} cal"
            
            iconHeat.visibility = if (session.heatEnabled) View.VISIBLE else View.GONE
            iconRotate.visibility = if (session.rotateEnabled) View.VISIBLE else View.GONE
        }
    }
}
