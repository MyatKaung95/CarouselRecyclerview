package com.jackandphantom.carousellayout

import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.customviewimple.model.DataModel
import com.jackandphantom.carousellayout.adapter.DataAdapter
import com.jackandphantom.carousellayout.databinding.ActivityMainBinding

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val list = ArrayList<DataModel>()
        for (i in 1..78) {
            list.add(DataModel(i, R.drawable.tarot, "This is Tarot Card"))
        }

        val adapter = DataAdapter(list)

        binding.recycler.apply {
            this.adapter = adapter
            set3DItem(true)
            setAlpha(true)
        }

        adapter.setOnItemSelectedListener(object : DataAdapter.OnItemSelectedListener {
            override fun onItemSelected(positions: List<Int>) {
                // handle item selection here
                binding.recycler.itemAnimator = null
                for (position in positions) {
                    val itemView = binding.recycler.layoutManager?.findViewByPosition(position)

                    // do your animation here
                    itemView?.let {
                        val translateY = ObjectAnimator.ofFloat(it, View.TRANSLATION_Y, 0f, -200f)
                        val alpha = ObjectAnimator.ofFloat(it, View.ALPHA, 1f, 1f)
                        val animatorSet = AnimatorSet().apply {
                            playTogether(translateY, alpha)
                            duration = 200
                        }
                        animatorSet.start() // start the animation
                    }
                }
            }
        })
    }
}