package com.madroid.webrtc.adapters

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.transition.Hold
import com.madroid.webrtc.R
import com.madroid.webrtc.model.Message

//
// Created by Soumik on 4/25/2021.
// piyal.developer@gmail.com
//

class ChatAdapter(private val list:List<Message>):RecyclerView.Adapter<ChatAdapter.Holder>() {

    inner class Holder(itemView:View):RecyclerView.ViewHolder(itemView) {

        @SuppressLint("SetTextI18n")
        fun bind(data:Message?) {
            itemView.apply {
                findViewById<TextView>(R.id.tv_nickname).text = "${data?.nickName}: "
                findViewById<TextView>(R.id.tv_message).text = data?.message
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder {
        return  Holder(LayoutInflater.from(parent.context).inflate(R.layout.item_chat,parent,false))
    }

    override fun getItemCount(): Int {
        return list.size
    }

    override fun onBindViewHolder(holder: Holder, position: Int) {
       holder.bind(list[position])
    }
}