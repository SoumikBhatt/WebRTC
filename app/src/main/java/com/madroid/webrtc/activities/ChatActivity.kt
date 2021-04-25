package com.madroid.webrtc.activities

import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.databinding.DataBindingUtil
import androidx.recyclerview.widget.DefaultItemAnimator
import androidx.recyclerview.widget.LinearLayoutManager
import com.madroid.webrtc.R
import com.madroid.webrtc.adapters.ChatAdapter
import com.madroid.webrtc.databinding.ActivityChatBinding
import com.madroid.webrtc.model.Message
import io.socket.client.IO
import io.socket.client.Socket
import org.json.JSONException
import org.json.JSONObject
import java.net.URISyntaxException


class ChatActivity : AppCompatActivity() {

    companion object {
        private const val TAG = "ChatActivity"
    }

    private lateinit var binding: ActivityChatBinding
    private var messageList:ArrayList<Message> ? = ArrayList()
    private var mAdapter: ChatAdapter ? =null
    private var socket: Socket?=null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = DataBindingUtil.setContentView(this,
            R.layout.activity_chat
        )


        try {
            socket = IO.socket("https://desolate-brook-24962.herokuapp.com/")
            socket?.connect()//https://secret-badlands-69635.herokuapp.com/
            socket?.emit("join","Wasik")
        } catch (e:URISyntaxException) {
            e.localizedMessage
            Log.e(TAG, "onCreate: Error: ${e.localizedMessage}")
        }

        binding.messagelist.apply {
            layoutManager = LinearLayoutManager(this@ChatActivity)
            itemAnimator = DefaultItemAnimator()
            setHasFixedSize(true)
            adapter = ChatAdapter(messageList!!)
        }

        binding.send.setOnClickListener {
            if (binding.message.text.isNotEmpty()) {
                socket?.emit("messagedetection","Wasik",binding.message.text.toString())
//                messageList?.add(Message("Wasik",binding.message.text.toString()))
//
//                // add the new updated list to the dapter
//                mAdapter = ChatAdapter(messageList!!)
//
//                // notify the adapter to update the recycler view
//                mAdapter?.notifyDataSetChanged()
                binding.message.setText(" ");
            }
        }

        //implementing socket listeners
        socket?.on("userjoinedthechat") { args ->
            runOnUiThread {
                val data = args[0] as String
                Toast.makeText(this@ChatActivity, data, Toast.LENGTH_SHORT).show()
            }
        }
        socket?.on("userdisconnect") { args ->
            runOnUiThread {
                val data = args[0] as String
                Toast.makeText(this@ChatActivity, data, Toast.LENGTH_SHORT).show()
            }
        }

        socket?.on("message") { args ->
            runOnUiThread {
                val data = args[0] as JSONObject
                try {
                    //extract data from fired event
                    val nickname = data.getString("senderNickname")
                    val message = data.getString("message")

                    // make instance of message
                    val m = Message(nickname, message)


                    //add the message to the messageList
                    messageList?.add(m)

                    // add the new updated list to the dapter
                    mAdapter = ChatAdapter(messageList!!)

                    // notify the adapter to update the recycler view
                    mAdapter?.notifyDataSetChanged()

                    //set the adapter for the recycler view
                    binding.messagelist.adapter = mAdapter
                } catch (e: JSONException) {
                    e.printStackTrace()
                }
            }
        }

    }

    override fun onDestroy() {

        socket?.disconnect()
        super.onDestroy()
    }
}