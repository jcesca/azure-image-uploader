package ie.nuigalway.jcesca;


import org.slf4j.Logger;
import org.slf4j.LoggerFactory;


/*
 * Based on:
 * https://github.com/Azure-Samples/cognitive-services-java-sdk-samples/blob/master/Vision/CustomVision/src/main/java/com/microsoft/azure/cognitiveservices/vision/customvision/samples/CustomVisionSamples.java
 * https://docs.microsoft.com/en-us/azure/cognitive-services/custom-vision-service/java-tutorial-od
 */
public class Application {


	final static Logger logger = LoggerFactory.getLogger(Application.class);
	

	/**
	 * Main class that wraps all operations:
	 * - Connects to Azure
	 * - Creates Project
	 * - Creates a Tag
	 * - Lists all XMLs from "directory"
	 * - Loads all images referred on the XML with mapped tag to a list
	 * - Adds all images with mapped tag to the project
	 */
	public static void main(String[] args) {
		logger.info("STARTED!");

		// Load environment variables
		final String trainingApiKey = System.getenv("AZURE_CUSTOMVISION_TRAINING_API_KEY");
		final String predictionApiKey = System.getenv("AZURE_CUSTOMVISION_PREDICTION_API_KEY");
		final String endpoint = System.getenv("AZURE_CUSTOMVISION_ENDPOINT");
		final String directory = System.getenv("IMAGES_DIR");
		final String testDir = System.getenv("IMAGE_TEST_DIR");
		
		// Variables for Project Name and Description
		String projectName = "ALIVE_Validation";
		String projectDescription = "ALIVE Project Validation";
		String modelName = "ALIVE_Model";
		
		ImageUploader uploader = new ImageUploader();
		uploader.connect(endpoint, trainingApiKey, predictionApiKey);
		uploader.createProject(projectName, projectDescription);
		
		uploader.loadImagesFromFiles(directory);
		
		uploader.train(modelName);
		uploader.uploadTestImages(testDir, modelName);
		
		logger.info("FINISHED!");
	}



}
