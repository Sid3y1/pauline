package fr.utc.assos.payutc;

import java.util.ArrayList;

import android.animation.ArgbEvaluator;
import android.animation.ObjectAnimator;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.os.Bundle;
import android.os.Vibrator;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.Toast;
import fr.utc.assos.payutc.adapters.ListItemAdapter;
import fr.utc.assos.payutc.api.ApiTask;
import fr.utc.assos.payutc.api.POSS.TransactionResult;
import fr.utc.assos.payutc.views.PanierSummary;

public class ConfirmPaymentActivity extends BaseActivity {
	public final static String LOG_TAG = "ConfirmPayment";
	
	ArrayAdapter<Item> mAdapter;
	
	private PanierSummary mPanierSummary;
	
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Log.d(LOG_TAG, "onCreate ConfirmPayment");
        setContentView(R.layout.confirm);

        mPanierSummary = (PanierSummary) findViewById(R.id.confirm_panier_summary);
        mPanierSummary.set(mSession);
        
        ListView lv = (ListView)findViewById(R.id.confirm_list);

        mAdapter = new ListItemAdapter(this, R.layout.list_item, mSession.getItems());
        
        lv.setAdapter(mAdapter);
        new DownloadImgTask(mAdapter, PaulineActivity.imageCache).execute(mSession.getItems().toArray(new Item[mSession.getItems().size()]));
    }
    
    public void onClickCancel(View _view) {
    	finish();
    }
    
    public void onClickDebugOk(View _view) {
    	onResultTransaction(PaulineActivity.POSS.new TransactionResult("thomas", "recouvreux", 44), null);
    }
    
    public void onClickDebugFail(View _view) {
    	onResultTransaction(null, "Plus de details en mode normal");
    }
    
    protected void onResultTransaction(TransactionResult transactionResult, String lastExceptionMessage) {
    	Vibrator v = (Vibrator) getSystemService(ConfirmPaymentActivity.VIBRATOR_SERVICE);
        final View screen = findViewById(R.id.confirm_layout);
    	if (transactionResult!=null) {
    		// Only perform this pattern one time (-1 means "do not repeat")
    		v.vibrate(300);
    		if (mSession.getHomeChoice() == PaulineSession.VENTE_LIBRE) {
    			stop(true);
    		}
    		else {
    	        ObjectAnimator colorFade = ObjectAnimator.ofObject(
    	        					screen, 
    	        					"backgroundColor", 
    	        					new ArgbEvaluator(),
    	        					0xffffffff, 0xff00ff00, 0xffffffff);
    			colorFade.setDuration(700);
        		colorFade.setIntValues();
                colorFade.start();
    			Toast.makeText(this, R.string.success_transaction, Toast.LENGTH_SHORT).show();
    		}
    	}
    	else {
    		long[] pattern = {
    		    0,  // Start immediately
    		    300, 100, 300
    		};
    		v.vibrate(pattern, -1);
    		screen.setBackgroundColor(0xffff0000);
    		AlertDialog.Builder builder = new AlertDialog.Builder(this);
    		builder.setTitle("Échec de la transaction")
    			.setMessage(lastExceptionMessage)
    			.setNegativeButton("J'ai compris", new DialogInterface.OnClickListener() {
    		           public void onClick(DialogInterface dialog, int id) {
   		        			screen.setBackgroundColor(0xffffffff);
    		                dialog.cancel();
    		           }});
    		
    		builder.create().show();
    	}
    }
    
    protected void startTransaction(String id) {
    	Log.d(LOG_TAG,"startTransaction");
    	mSession.setBuyerId(id);
    	new TransactionTask(mSession.getBuyerId() ,mSession.getItems()).execute();
    }
    
    @Override
    protected void onIdentification(String id) {
    	Log.d(LOG_TAG, "onIdentification");
    	startTransaction(id);
    }
    

    protected class TransactionTask extends ApiTask<Integer, Integer, Integer> {
    	private ArrayList<Integer> mIds;
    	private String mIdBuyer;
    	private TransactionResult r=null;
    	
    	public TransactionTask(String id, ArrayList<Item> items) {
    		super("Transaction", ConfirmPaymentActivity.this,
    				"Transaction en cours...", 0);
			mIds = new ArrayList<Integer>();
			for (int i=0; i<items.size(); ++i) {
				Item item = items.get(i);
				mIds.add(item.getId());
			}
    		mIdBuyer = id;
    	}
    	
    	
		@Override
		protected boolean callSoap() throws Exception {
			r = PaulineActivity.POSS.transaction(mIdBuyer, mIds, "via Pauline");
			return true;
		}
		
		@Override
		protected void onPostExecute(Integer osef) {
			super.onPostExecute(osef);
			Log.d(ConfirmPaymentActivity.LOG_TAG, "result transaction : "+r);
			String lastExceptionMessage = "";
			if (lastException!=null) {
				lastExceptionMessage = lastException.getMessage();
			}
			onResultTransaction(r, lastExceptionMessage);
		}
    }
}
