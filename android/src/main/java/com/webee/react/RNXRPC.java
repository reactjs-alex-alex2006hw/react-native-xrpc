
package com.webee.react;

import com.facebook.react.bridge.Promise;
import com.facebook.react.bridge.ReactApplicationContext;
import com.facebook.react.bridge.ReactContextBaseJavaModule;
import com.facebook.react.bridge.ReactMethod;
import com.facebook.react.bridge.ReadableArray;
import com.facebook.react.bridge.ReadableMap;
import com.facebook.react.module.annotations.ReactModule;

import java.util.HashMap;
import java.util.Map;

import rx.Observable;
import rx.Subscriber;
import rx.subjects.AsyncSubject;
import rx.subjects.PublishSubject;

import static com.webee.react.RNXRPCClient.requests;

@ReactModule(name = "XRPC")
public class RNXRPC extends ReactContextBaseJavaModule {
    public static final String XRPC_EVENT = "__XRPC__";
    public static final int XRPC_EVENT_CALL = 0;
    public static final int XRPC_EVENT_REPLY = 1;
    public static final int XRPC_EVENT_REPLY_ERROR = 2;
    public static final int XRPC_EVENT_EVENT = 3;
    private static final Map<String, Object> constants;
    private static final PublishSubject<Event> eventSubject = PublishSubject.create();

    static {
        constants = new HashMap<>();
        constants.put("XRPC_EVENT", XRPC_EVENT);
        constants.put("EVENT_CALL", XRPC_EVENT_CALL);
        constants.put("EVENT_REPLY", XRPC_EVENT_REPLY);
        constants.put("EVENT_REPLY_ERROR", XRPC_EVENT_REPLY_ERROR);
        constants.put("EVENT_EVENT", XRPC_EVENT_EVENT);
    }

    public RNXRPC(ReactApplicationContext reactContext) {
        super(reactContext);
    }

    @Override
    public String getName() {
        return "XRPC";
    }

    @Override
    public Map<String, Object> getConstants() {
        return constants;
    }

    @ReactMethod
    public void emit(final int event, final ReadableArray args) {
        switch (event) {
            case XRPC_EVENT_REPLY:
                handleCallReply(args);
                break;
            case XRPC_EVENT_REPLY_ERROR:
                handleCallReplyError(args);
                break;
            case XRPC_EVENT_EVENT:
                handleEvent(args);
            default:
                break;
        }
    }

    @ReactMethod
    public void call(final String proc, final ReadableArray args, final ReadableMap kwargs, final Promise promise) {
        Subscriber<? super Request> subscriber = RNXRPCClient.procedures.get(proc);
        subscriber.onNext(new Request(args, kwargs, promise));
    }

    private void handleEvent(ReadableArray xargs) {
        String event = xargs.getString(0);
        ReadableArray args = xargs.getArray(1);
        ReadableMap kwargs = xargs.getMap(2);
        eventSubject.onNext(new Event(event, args, kwargs));
    }

    private void handleCallReply(ReadableArray xargs) {
        String rid = xargs.getString(0);
        AsyncSubject<Reply> replySubject = RNXRPCClient.requests.remove(rid);
        if (replySubject == null) {
            return;
        }

        ReadableArray args = xargs.getArray(1);
        ReadableMap kwargs = xargs.getMap(2);

        replySubject.onNext(new Reply(args, kwargs));
        replySubject.onCompleted();
    }

    private void handleCallReplyError(ReadableArray xargs) {
        String rid = xargs.getString(0);
        AsyncSubject<Reply> replySubject = requests.remove(rid);
        if (replySubject == null) {
            return;
        }

        String error = xargs.getString(1);

        int s = xargs.size();
        ReadableArray args = null;
        ReadableMap kwargs = null;
        if (s > 2) {
            args = xargs.getArray(2);
        }
        if (s > 3) {
            kwargs = xargs.getMap(3);
        }

        replySubject.onError(new XRPCError(error, args, kwargs));
    }

    public static Observable<Event> event() {
        return eventSubject.asObservable();
    }
}