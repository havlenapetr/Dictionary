package cz.havlena.dictionary;

import java.io.ByteArrayInputStream;
import java.io.IOException;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.xml.sax.Attributes;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import org.xml.sax.helpers.DefaultHandler;

public class DictionaryXmlParser {
	
	private static final boolean ANDROID = true;
	
	private SAXParserFactory mSAXfactory;
	private XmlHandler mHandler;

	public void init() {
		mSAXfactory = SAXParserFactory.newInstance();
		mHandler = new XmlHandler();
	}
	
	public String parseToHtml(String xml) throws ParserConfigurationException, SAXException, IOException {
		return parseToHtml(xml.getBytes());
	}
	
	public String parseToHtml(byte[] xml) throws ParserConfigurationException, SAXException, IOException {
		SAXParser sp = mSAXfactory.newSAXParser();
		XMLReader xr = sp.getXMLReader();
		mHandler.init(DictionaryService.FORMAT_HTML);
		xr.setContentHandler(mHandler);
		xr.parse(new InputSource(new ByteArrayInputStream(xml)));
		return mHandler.getHtml();
	}
	
	public DictionaryElement parseToElement(String xml) throws IOException, SAXException, ParserConfigurationException {
		SAXParser sp = mSAXfactory.newSAXParser();
		XMLReader xr = sp.getXMLReader();
		mHandler.init(DictionaryService.FORMAT_ELEMENT);
		xr.setContentHandler(mHandler);
		xr.parse(new InputSource(new ByteArrayInputStream(xml.getBytes())));
		return mHandler.getElement();
	}
	
	private class XmlHandler extends DefaultHandler {
		
		private String html;
		private DictionaryElement mElement;
		private boolean skip;
		private int mFormat;
		private int mParseState;
		
		public String getHtml() {
			return html;
		}
		
		public DictionaryElement getElement() {
			return mElement;
		}
		
		public void init(int format) throws ParserConfigurationException {
			mFormat = format;
			skip = false;
			if(mFormat == DictionaryService.FORMAT_HTML)
				html = "";
			else if(mFormat == DictionaryService.FORMAT_ELEMENT)
				mElement = new DictionaryElement();
			else throw new ParserConfigurationException("Bad format for XmlHandler");
		}
		
		@Override
		public void startElement(String uri, String localName, String qName,
				Attributes attributes) throws SAXException {
			String element = (ANDROID) ? localName : qName;
			
			// expression
			if(element.matches("ex")) {
				mParseState = DictionaryElement.EXPRESSION;
				if(mFormat == DictionaryService.FORMAT_HTML) html += "<b>";
		    }
			// definition
		    else if(element.matches("def")) {
		    	mParseState = DictionaryElement.DEFINITION;
		        skip = false;
		    }
			// senses <sn no="1">....</sn>
		    else if(element.matches("sn")) {
		    	mParseState = DictionaryElement.SENSES;
		    	if(mFormat == DictionaryService.FORMAT_HTML) {
			        if(html.endsWith("</ol>")) {
			           // html.chop(5);
			        }
			        else {
			            html += "<ol>";
			        }
			        html += "<li>";
		    	}
		        skip = false;
		    }
			// <entry key="Hell">
		    if(element.matches("entry")){
		    	String key = attributes.getValue("key");
		    	if(mFormat == DictionaryService.FORMAT_HTML) {
			        html += "<h1>";
			        html += key;
			        html += "</h1>";
		    	} else if(mFormat == DictionaryService.FORMAT_ELEMENT) {
		    		mElement.key = key; 
		    	}
		        skip = true;
		    }
		}
		
		@Override
		public void characters(char[] ch, int start, int length)
				throws SAXException {		
			if(skip) return;
			
			String chars = new String(ch, start, length);
			switch(mFormat) {
			case DictionaryService.FORMAT_HTML:
				html += chars;
				break;
				
			case DictionaryService.FORMAT_ELEMENT:
				switch(mParseState) {
				case DictionaryElement.DEFINITION:
					mElement.definition = chars;
					break;
					
				case DictionaryElement.EXPRESSION:
					mElement.expression = chars;
					break;
					
				case DictionaryElement.SENSES:
					mElement.senses = chars;
					break;
				}
				break;
			}
		}
		
		@Override
		public void endElement(String uri, String localName, String qName)
				throws SAXException {
			
			if(mFormat != DictionaryService.FORMAT_HTML) {
				return;
			}
			
			if(qName == "ex") {
		        html += "</b>";
		    }
		    else if(qName == "def") {
		        skip = true;
		    }
		    else if(qName == "sn") {
		        html += "</li></ol>";
		    }
		    else if(qName == "entry") {
		    	html += "</html>";
		    }
		}
		
		@Override
		public void fatalError(SAXParseException e) throws SAXException {
			html += "Parse error: " + e.getMessage();
		}
	
	}

}
