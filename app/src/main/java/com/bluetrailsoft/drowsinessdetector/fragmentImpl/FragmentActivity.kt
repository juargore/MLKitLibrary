package com.bluetrailsoft.drowsinessdetector.fragmentImpl

import android.annotation.SuppressLint
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import com.bluetrailsoft.drowsinessdetector.R

/**
 * Simple code that illustrates the use of a fragment running the Drowsiness Library.
 * */
class FragmentActivity : AppCompatActivity() {
    @SuppressLint("CommitTransaction")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_fragment)

        // just create an instance of FragmentOne and add it to container
        supportFragmentManager
            .beginTransaction()
            .add(R.id.container, FragmentOne())
            .commit()
    }
}
