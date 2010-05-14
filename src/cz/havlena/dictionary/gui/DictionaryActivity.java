package cz.havlena.dictionary.gui;

import java.io.File;
import java.io.FileNotFoundException;

import javax.xml.parsers.ParserConfigurationException;

import org.xml.sax.SAXException;

import cz.havlena.dictionary.DictionaryElement;
import cz.havlena.dictionary.DictionaryService;
import cz.havlena.dictionary.IDictionaryService;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AlertDialog.Builder;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.DialogInterface.OnClickListener;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.speech.tts.TextToSpeech;
import android.speech.tts.TextToSpeech.OnInitListener;
import android.text.Editable;
import android.text.Html;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.Window;
import android.widget.EditText;
import android.widget.TextView;

public class DictionaryActivity extends Activity {
	
	private static final String TAG = "DictionaryActivity";
	private static final String DICTIONARY_FILE = "gcide-entries.xml";
	private static final int FORMAT = DictionaryService.FORMAT_HTML;
	
	private static final int SEARCHING_FOUND = 1;
	private static final int SEARCHING_COMPLETED = 2;
	private static final int SEARCHING_STARTED = 3;
	private static final int SEARCHING_ERROR = -1;
	
	private static final int TTS_REQUEST = 1;
	
	private static final int MENU_TTS = 1;
	
	private DictionaryService mService;
	private TextToSpeech mTts;
	private TextToSpeechHandler mSpeechHandler;
	
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);
        setContentView(R.layout.main);
        
        EditText edtSearch = (EditText) findViewById(R.id.edtSearch);
        edtSearch.addTextChangedListener(new SearchHandler());
  
        checkTextToSpeech();
    }
    
    private void checkTextToSpeech() {
    	Intent checkIntent = new Intent();
    	checkIntent.setAction(TextToSpeech.Engine.ACTION_CHECK_TTS_DATA);
    	startActivityForResult(checkIntent, TTS_REQUEST);
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	menu.add(0, MENU_TTS, 0, "TextToSpeech");
    	return true;
    }
    
    /* Handles item selections */
    public boolean onOptionsItemSelected(MenuItem item) {
    	switch(item.getItemId()) {
    	case MENU_TTS:
    		if(mTts != null && mTts.isSpeaking()) {
    			mTts.stop();
    		}
    		return true;
    	}
    	return false;
    }
    
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    	if (requestCode == TTS_REQUEST) {
            if (resultCode == TextToSpeech.Engine.CHECK_VOICE_DATA_PASS) {
                // success, create the TTS instance
            	mSpeechHandler = new TextToSpeechHandler();
                mTts = new TextToSpeech(this, mSpeechHandler);
            } else {
            	Builder dialog = new AlertDialog.Builder(this);
            	dialog.setMessage(this.getString(R.string.tts_not_available));
            	dialog.setPositiveButton("Install", new OnClickListener() {
        			public void onClick(DialogInterface dialog, int which) {
        				Intent installIntent = new Intent();
        		        installIntent.setAction(
        		            TextToSpeech.Engine.ACTION_INSTALL_TTS_DATA);
        		        startActivity(installIntent);
        			}
        		});
            	dialog.setNegativeButton("Not using", new OnClickListener() {
					public void onClick(DialogInterface dialog, int which) {
						Log.w(TAG, "User doesn't want to use TTS service");
					}
				});
            	dialog.show();
            }
        }
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();
    	if(mTts != null) mTts.shutdown();
    }
    
    private void processError(Exception ex) {
    	Message msg = mHandler.obtainMessage(SEARCHING_ERROR);
		msg.obj = ex;
		mHandler.sendMessage(msg);
    }
    
    private Handler mHandler = new Handler() {
    	private boolean xmlError = false;
    	
    	@Override
    	public void handleMessage(Message msg) {
    		TextView txtResult = (TextView) findViewById(R.id.txtResult);
    		switch(msg.what) {
    		case SEARCHING_STARTED:
    			Log.w(TAG, "Searching started");
    			txtResult.setText("");
    			setTitle(getString(R.string.searching));
    			setProgressBarIndeterminateVisibility(true);
    			break;
    			
    		case SEARCHING_FOUND:
    			switch(FORMAT) {
    			case DictionaryService.FORMAT_HTML:
	    			String element = (String) msg.obj;
	    			if(element != null) {
		    			if(!xmlError)
		    				element = Html.fromHtml(element).toString();
		   
		    				txtResult.setText(txtResult.getText() + element + "\n" +
		    						"----------------------------------------------" + "\n");
		    		}
	    			break;
	    			
    			case DictionaryService.FORMAT_ELEMENT:
    				DictionaryElement el = (DictionaryElement) msg.obj;
	    			if(el != null) {
	    				txtResult.setText(txtResult.getText() + el.key + "\n"/* + el.definition + "\n" + 
	    						el.expression + "\n" + el.senses + "\n" +
	    						"----------------------------------------------" + "\n"*/);
	        			//txtResult.invalidate();
    				}
    				break;
    			}
    			
    			xmlError = false;
    			break;
    			
    		case SEARCHING_COMPLETED:
    			Log.w(TAG, "Searching stopped");
    			setTitle(getString(R.string.app_name));
    			setProgressBarIndeterminateVisibility(false);
    			if(txtResult.getText().length() == 0) {
    				txtResult.setText("no word found");
    				return;
    			}
    			
    			if(mSpeechHandler.isReady()) {
    				if(mTts.isSpeaking()) {
    					mTts.stop();
    				}
    				mTts.speak((String) txtResult.getText(), TextToSpeech.QUEUE_FLUSH, null);
    			}
    			break;
    			
    		case SEARCHING_ERROR:
    			Exception ex = (Exception) msg.obj;
    			if((ex instanceof ParserConfigurationException) ||
    					(ex instanceof SAXException)) {
    				xmlError = true;
    			}
	    		else {
	    			txtResult.setText("Error: " + ex.getMessage());
    			}
    			Log.e(TAG, "Searching error: " + ex.getMessage());
    			break;
    		}
    	}
    };
    
    private class TextToSpeechHandler implements OnInitListener {
    	private boolean ready = false;
    	
    	public boolean isReady() {
    		return ready;
    	}
    	
    	public void onInit(int status) {
        	Log.w(TAG, "TextToSpeech initzialized");
        	
        	/*int result = mTts.isLanguageAvailable(Locale.);
        	if(result != TextToSpeech.LANG_AVAILABLE) {
        		Log.e(TAG, "UK language isn't available [" + result + "]");
        		return;
        	}*/
        	
        	ready = true;
        }
    	
    }
    
    private class DictionaryHandler implements IDictionaryService {

		public void onFoundElement(Object element) {
			Message msg = mHandler.obtainMessage(SEARCHING_FOUND);
			msg.obj = element;
			mHandler.sendMessage(msg);
		}

		public void onSearchStop() {
			Message msg = mHandler.obtainMessage(SEARCHING_COMPLETED);
			mHandler.sendMessage(msg);
		}

		public void onSearchStart() {
			Message msg = mHandler.obtainMessage(SEARCHING_STARTED);
			mHandler.sendMessage(msg);
		}

		public void onError(Exception ex) {
			processError(ex);
		}
    	
    }
    
    private class SearchHandler implements TextWatcher {

		public void afterTextChanged(final Editable arg0) {
			mService.searchAsync(arg0.toString(), 10, FORMAT);
		}

		public void beforeTextChanged(CharSequence arg0, int arg1, int arg2, int arg3) {
			if(mService == null) {
				Log.w(TAG, "Initzializing dictionary service");
				
				mService = new DictionaryService();
				mService.setListener(new DictionaryHandler());
				try {
					mService.init(new File("/sdcard/" + DICTIONARY_FILE));
					Log.w(TAG, "Dictionary service initzialized");
				} catch (FileNotFoundException e) {
					processError(e);
				}
			}
		}

		public void onTextChanged(CharSequence arg0, int arg1, int arg2,
				int arg3) {}
    	
    }
}