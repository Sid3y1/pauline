package fr.utc.assos.payutc.soap;


import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Hashtable;
import java.util.List;

import org.ksoap2.HeaderProperty;
import org.ksoap2.SoapEnvelope;
import org.ksoap2.serialization.MarshalHashtable;
import org.ksoap2.serialization.SoapObject;
import org.ksoap2.serialization.SoapSerializationEnvelope;
import org.ksoap2.transport.HttpTransportSE;
import org.ksoap2.transport.HttpsTransportSE;
import org.xmlpull.v1.XmlPullParserException;

import android.util.Log;
import fr.utc.assos.payutc.Item;


public class PBuy {
	private final String TAG = "PBuy";
	
	private String host;
	private String namespace;
	private String path;
	private boolean ssl;
	HashMap<String, String> cookies = new HashMap<String, String>();
	
	public PBuy(String _host, String _path, String _namespace, boolean _ssl) {
		host = _host;
		path = _path;
		namespace = _namespace;
		ssl = _ssl;
	}
	
	public String getCasUrl() throws IOException, XmlPullParserException, ApiException {
		// Création de la requête SOAP
		SoapObject request = new SoapObject (namespace, "getCasUrl");
		Object result = soap(request);
		Log.d("getCasUrl", result.toString());
		String url = result.toString();
		return url;
	}
	
    public boolean loadPos(String ticket, String service, int poi_id) throws IOException, XmlPullParserException, ApiException {
		// Création de la requête SOAP
		SoapObject request = new SoapObject (namespace, "loadPos");
		request.addProperty("ticket", ticket);
		request.addProperty("service", service);
		request.addProperty("poi_id", poi_id);
		Object result = soap(request);
		Log.d("loadPos", result.toString());
		return true;
    }
    
    public boolean unload() throws IOException, XmlPullParserException, ApiException {
		SoapObject request = new SoapObject (namespace, "logout");
		Object result = soap(request);
		Log.d("unload", result.toString());
    	return true;
    }
	
	@SuppressWarnings("rawtypes")
	public ArrayList<Item> getArticles() throws IOException, XmlPullParserException, ApiException {
		// Création de la requête SOAP
		SoapObject request = new SoapObject (namespace, "getArticles");
		Object result = soap(request);
		Log.d("getArticles", result.toString());
		@SuppressWarnings("unchecked")
		Hashtable<String,Hashtable<Integer,Hashtable> > cat_n_art = (Hashtable)result;
		ArrayList<Item> articles = new ArrayList<Item>();
		for (Hashtable ht : cat_n_art.get("articles").values()) {
			articles.add(new Item(ht));
		}
		return articles;
	}
	
	public class TransactionResult {
		public String firstName;
		public String lastName;
		public int solde;
		public TransactionResult(String first_name, String last_name, int _solde) {
			firstName = first_name;
			lastName = last_name;
			solde = _solde;
		}
	}
	
	public TransactionResult transaction(String badge_id, ArrayList<Integer> ids, String trace) throws IOException, XmlPullParserException, ApiException {
		String s_ids = "";
		for (int id : ids) {
			s_ids += " "+id;
		}
		SoapObject request = new SoapObject (namespace, "transaction");
		request.addProperty("badge_id", badge_id);
		request.addProperty("obj_ids", s_ids);
		request.addProperty("trace", trace);
		Object result = (Object)soap(request);
		Log.d("transaction", result.toString());
		@SuppressWarnings("rawtypes")
		Hashtable ht = (Hashtable)result;
		String first_name = (String) ht.get("firstname");
		String last_name = (String) ht.get("lastname");
		int solde = Integer.parseInt((String)ht.get("solde"));
		TransactionResult retour = new TransactionResult(first_name, last_name, solde); 
		return retour;
	}
    
	private Object soap (SoapObject request) 
			throws IOException, XmlPullParserException, ApiException
	{
		Log.d(TAG, "soap");
		String soap_action = namespace + "#" + request.getName();
		Log.d(TAG, "action : "+soap_action);
		
		// Toutes les données demandées sont mises dans une enveloppe.
		SoapSerializationEnvelope envelope = new SoapSerializationEnvelope (
				SoapEnvelope.VER11);
		envelope.setOutputSoapObject (request);
		// Pour que les map puissent être parsé
		new MarshalHashtable().register(envelope);
		
		Log.d(TAG, "fin envelope");
		
		HttpTransportSE androidHttpTransport;
		if (ssl) {
			androidHttpTransport = new HttpsTransportSE (host, 443, path, 10000);
		}
		else {
			androidHttpTransport = new HttpTransportSE(host+path, 10000);
		}
			
		//Ceci est optionnel, on l'utilise pour savoir si nous voulons ou non utiliser 
		//un paquet "sniffer" pour vérifier le message original (androidHttpTransport.requestDump)
		androidHttpTransport.debug = true; 

		//Envoi de la requête
		@SuppressWarnings("unchecked")
		List<HeaderProperty> respHeaders = (List<HeaderProperty>)androidHttpTransport.call(soap_action, envelope, get_headers());

		Log.d(TAG, "fin call");
		
		update_cookies(respHeaders);

		Log.d(TAG, "fin cookie");
		
		Log.d(TAG, "bodyIn : "+envelope.bodyIn);
		@SuppressWarnings("rawtypes")
		Hashtable result = (Hashtable)envelope.getResponse();
		
		if (result.get("success") == null) {
			int err_code = 42;
			String err_msg = null;
			try {
				err_code = (Integer) result.get("error");
				err_msg = (String) result.get("error_msg");
			} catch (Exception e) {
				Log.e(TAG, "soap", e);
			}
			throw new ApiException(err_code, err_msg);
		}
		
		return result.get("success");
	}
	
	synchronized List<HeaderProperty> get_headers() {
		List<HeaderProperty> headers = new ArrayList<HeaderProperty>();
        for (String cookie:cookies.keySet()) {
        	headers.add(new HeaderProperty("Cookie", cookie + "=" + cookies.get(cookie)));
        }
        return headers;
	}
	
	synchronized void update_cookies(List<HeaderProperty> respHeaders) {
		if (respHeaders != null) {
            for (int i = 0; i < respHeaders.size(); ++i) {
                HeaderProperty hp = (HeaderProperty)respHeaders.get(i);
                String key = hp.getKey();
                String value = hp.getValue();
                if (key!=null && value!=null) {
                    if (key.equalsIgnoreCase("set-cookie")){
            			Log.d("soap", hp.getKey() + " -> " + hp.getValue());
                    	String[] cookieSplit = value.replace(" ", "").split(";");
                    	for (String cookieString : cookieSplit) {
                    		String cookieKey = cookieString.substring(0, cookieString.indexOf("="));
                    		String cookieValue = cookieString.substring(cookieString.indexOf("=")+1);
                    		cookies.put(cookieKey, cookieValue);
                    		Log.d("soap", cookieKey + " = " + cookieValue);
                    	}
                    }
                }
            }
        }
	}
}
