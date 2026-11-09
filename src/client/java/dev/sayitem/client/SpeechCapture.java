package dev.sayitem.client;

import dev.speechlib.MicrophoneCapture;
import dev.speechlib.SpeechEngine;
import java.util.List;
import java.util.function.Consumer;

/** Connects SayItem's settings and HUD messages to SpeechLib's recorder. */
public final class SpeechCapture {
    private final ClientSettings settings;
    private final MicrophoneCapture microphone;
    public SpeechCapture(ClientSettings settings,SpeechEngine engine){this.settings=settings;microphone=new MicrophoneCapture(engine);}
    public boolean isRecording(){return microphone.isRecording();}
    public boolean isBusy(){return microphone.isBusy();}
    public static List<String> microphones(){return MicrophoneCapture.microphones();}
    public void start(String language,Runnable processing,Consumer<String> status,Consumer<String> transcript,Consumer<String> error){
        microphone.start(language,settings.microphoneName,processing,message->status.accept(message.equals("Listening")
            ?"Listening… release your speak key when finished.":"Recognizing locally…")).whenComplete((text,failure)->{
            if(failure==null){
                if(text.length()>256)error.accept("Say only the displayed item name, then try again.");
                else transcript.accept(text);
            }else if(!(failure instanceof java.util.concurrent.CancellationException)){
                Throwable cause=failure;while(cause.getCause()!=null)cause=cause.getCause();
                error.accept("Speech unavailable: "+(cause.getMessage()==null?cause.getClass().getSimpleName():cause.getMessage())
                    +". Open SayItem settings (K) to check speech and microphone settings.");
            }
        });
    }
    public void finish(){microphone.finish();}
    public void cancel(){microphone.cancel();}
}
