package misk.client

import okhttp3.Call

interface CallFactoryWrapper {
  fun wrap(action: ClientAction, delegate: Call.Factory): Call.Factory?
}
