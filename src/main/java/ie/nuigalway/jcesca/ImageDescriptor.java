package ie.nuigalway.jcesca;

import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.DocumentBuilder;
import org.w3c.dom.Document;
import org.w3c.dom.NodeList;
import org.w3c.dom.Element;
import java.io.File;
import java.nio.file.Files;
import java.util.ArrayList;

public class ImageDescriptor {

	private String fileName;
	private ArrayList<ObjectTag> tagList = new ArrayList<ObjectTag>();
	private byte[] fileContents;

	public ImageDescriptor(String directory, String xmlFileName) {

		// Based on https://www.mkyong.com/java/how-to-read-xml-file-in-java-dom-parser/
		try {
			// Open the XML file to parse it
			File fXmlFile = new File(directory + "/" + xmlFileName);
			DocumentBuilderFactory dbFactory = DocumentBuilderFactory.newInstance();
			DocumentBuilder dBuilder = dbFactory.newDocumentBuilder();
			Document doc = dBuilder.parse(fXmlFile);

			// optional, but recommended
			// read this -
			// http://stackoverflow.com/questions/13786607/normalization-in-dom-parsing-with-java-how-does-it-work
			doc.getDocumentElement().normalize();

			// Reads data from the XML file into the object
			// File Name
			NodeList nListFilename = doc.getElementsByTagName("filename");
			String tmpFileName = nListFilename.item(0).getTextContent();
			this.fileName = xmlFileName.substring(0, xmlFileName.length() - 3) + tmpFileName.substring(tmpFileName.length()-3);

			// Picture Size
			Element elSize = (Element) doc.getElementsByTagName("size").item(0);
			double width = Double.parseDouble(elSize.getElementsByTagName("width").item(0).getTextContent());
			double height = Double.parseDouble(elSize.getElementsByTagName("height").item(0).getTextContent());
			

			int numObjects = doc.getElementsByTagName("object").getLength();
			System.out.println("Amount of objects: " + numObjects);
			for (int i = 0; i < numObjects; i++) {
				ObjectTag tag = new ObjectTag();

				// Object tag, that contains the area
				Element currentObject = (Element) doc.getElementsByTagName("object").item(i);
				
				// Area
				// The xml file provided uses a different notation and has some flaws
				// - The notation used is considering the object area as pixels, while Azure
				// uses a double value from 0 (min) to 1 (max). This is why it's being divided
				// by width and height
				// - The YMin and YMax are inverted (on the xml YMin is always higher than YMax
				
				tag.setName(currentObject.getElementsByTagName("name").item(0).getTextContent());

				tag.setXmin(Double.parseDouble(currentObject.getElementsByTagName("xmin").item(0).getTextContent()) / width);
				tag.setXmax(Double.parseDouble(currentObject.getElementsByTagName("xmax").item(0).getTextContent()) / width);
				
				double ymin = Double.parseDouble(currentObject.getElementsByTagName("ymin").item(0).getTextContent()) / height;
				double ymax = Double.parseDouble(currentObject.getElementsByTagName("ymax").item(0).getTextContent()) / height;
						
				// At the XML YMin and YMax are inverted
				if (ymin > ymax) {
					tag.setYmin(ymax); 
					tag.setYmax(ymin); 				
				} else {
					tag.setYmax(ymax); 
					tag.setYmin(ymin); 				
				}
				this.tagList.add(tag);
			}


			// Load Image Content
			File file = new File(directory + "/" + this.fileName);
			this.fileContents = Files.readAllBytes(file.toPath());

		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	// Getters and Setters for Class Attributes
	
	public ArrayList<ObjectTag> getTagList() {
		return tagList;
	}

	public void setTagList(ArrayList<ObjectTag> tagList) {
		this.tagList = tagList;
	}

	public String getFileName() {
		return fileName;
	}

	public void setFileName(String fileName) {
		this.fileName = fileName;
	}


	public byte[] getFileContents() {
		return fileContents;
	}

	public void setFileContents(byte[] fileContents) {
		this.fileContents = fileContents;
	}

	public String toString() {
		return this.fileName;
	}

}
