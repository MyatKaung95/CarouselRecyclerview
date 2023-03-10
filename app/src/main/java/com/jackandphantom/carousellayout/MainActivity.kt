package com.jackandphantom.carousellayout

import android.os.Bundle
import android.view.View.OnClickListener
import androidx.appcompat.app.AppCompatActivity
import com.example.customviewimple.model.DataModel
import com.jackandphantom.carousellayout.adapter.DataAdapter
import com.jackandphantom.carousellayout.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val list = ArrayList<DataModel>()

        for(i in 1..78) {
            list.add(DataModel(R.drawable.tarot,"This is Tarot Card"))
        }

        val adapter = DataAdapter(list)

        binding.recycler.apply {
            this.adapter = adapter
            set3DItem(true)
            setAlpha(true)
        }

        //Trigger the button and put your useCase to test different cases of adapter
        binding.button.setOnClickListener {
            adapter.removeData()
        }
    }
}