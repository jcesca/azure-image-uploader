package ie.nuigalway.jcesca;

<<<<<<< HEAD
=======
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
>>>>>>> 63b65947f43f217fe1651be2dc2a2e3fd2894d62

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

<<<<<<< HEAD
=======
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.CustomVisionPredictionClient;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.CustomVisionPredictionManager;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.DetectImageOptionalParameter;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.ImagePrediction;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.CustomVisionTrainingClient;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.CustomVisionTrainingManager;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.Trainings;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Classifier;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Domain;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.DomainType;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.ImageFileCreateBatch;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.ImageFileCreateEntry;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Iteration;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Project;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Region;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Tag;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.TrainProjectOptionalParameter;

>>>>>>> 63b65947f43f217fe1651be2dc2a2e3fd2894d62

/*
 * Based on:
 * https://github.com/Azure-Samples/cognitive-services-java-sdk-samples/blob/master/Vision/CustomVision/src/main/java/com/microsoft/azure/cognitiveservices/vision/customvision/samples/CustomVisionSamples.java
 * https://docs.microsoft.com/en-us/azure/cognitive-services/custom-vision-service/java-tutorial-od
 */
public class Application {

<<<<<<< HEAD

	final static Logger logger = LoggerFactory.getLogger(Application.class);
	
	// Class Variables
	static String modelName = "myModel";
=======
	// Read Environment Variables
	final static String trainingApiKey = System.getenv("AZURE_CUSTOMVISION_TRAINING_API_KEY");
	final static String predictionApiKey = System.getenv("AZURE_CUSTOMVISION_PREDICTION_API_KEY");
	final static String endpoint = System.getenv("AZURE_CUSTOMVISION_ENDPOINT");
	final static String directory = System.getenv("IMAGES_DIR");
	final static String testDir = System.getenv("IMAGE_TEST_DIR");
	final static Logger logger = LoggerFactory.getLogger(Application.class);
	
	// Class Variables
	static Project project;
	static Trainings trainer;
	static List<Tag> tagList;
	static CustomVisionPredictionClient predictClient;
	static ArrayList<ImageFileCreateEntry> imgList = new ArrayList<ImageFileCreateEntry>();
	static String publishedModelName = "myModel";
	static Iteration iteration = null;
>>>>>>> 63b65947f43f217fe1651be2dc2a2e3fd2894d62


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
<<<<<<< HEAD
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
		
		ImageUploader uploader = new ImageUploader();
		uploader.connect(endpoint, trainingApiKey, predictionApiKey);
		uploader.createProject(projectName, projectDescription);
		
		uploader.loadImagesFromFiles(directory);
		
		uploader.train(modelName);
		uploader.uploadTestImages(testDir, modelName);
		
		logger.info("FINISHED!");
=======
		connect();
		

		createProject();
		

		File[] xmlList = listAllXMLs(directory);

		int batch = 1;
		int maxBatchSize = 50;
		for (File f : xmlList) {
			ImageDescriptor imgDesc = new ImageDescriptor(directory, f.getName());
			addImageToList(imgDesc);	
			if (imgList.size() == maxBatchSize) {
				logger.info("Uploading Batch: " + batch);
				addImageListToProject();
				imgList = new ArrayList<ImageFileCreateEntry>();
				batch++;
			}
		}
		if (imgList.size() > 0) {
			logger.info("Uploading Batch: " + batch);
			addImageListToProject();
		}
		
		train();
		uploadTestImages(testDir);
	}

	/**
	 * Connects to Azure using Endpoint and trainingApiKey variables.
	 * Stores Trainer Client on trainer 
	 */
	public static void connect() {
		try {
			// Authenticate using Azure API - Trainer
			logger.info("Connecting Train Client");
			CustomVisionTrainingClient  trainClient = CustomVisionTrainingManager
					.authenticate("https://" + endpoint + "/customvision/v3.0/training/", trainingApiKey).withEndpoint(endpoint);
			trainer = trainClient.trainings();

			
			// Authenticate using Azure API - Predictor
			logger.info("Connecting Prediction Client");
			predictClient = CustomVisionPredictionManager.authenticate("https://{Endpoint}/customvision/v3.0/prediction/", predictionApiKey).withEndpoint(endpoint);
		} catch (Exception e) {
			e.printStackTrace();
		}

	}

	/**
	 * Creates project on Azure
	 */
	public static void createProject() {
		// Create a Project on Azure (customvision.ai)
		logger.info("Creating project...");
		project = trainer.createProject().withName("ALIVE_Validation") // Project Name
				.withDescription("ALIVE Project Validation")
				.withDomainId(getObjectDetectionDomain().id()) // With Object Detection Domain set
				.withClassificationType(Classifier.MULTILABEL.toString())
				.execute();
		tagList = trainer.getTags().withProjectId(project.id()).execute();
		logger.info("Project ID: " + project.id());
		
	}

	
	/**
	 *	Finds and returns the Object Detection domain 
	 */
	public static Domain getObjectDetectionDomain() {
		// Get the Object Detection Domain
		Domain objectDetectionDomain = null;
		List<Domain> domains = trainer.getDomains();
		for (final Domain domain : domains) {
			if (domain.type() == DomainType.OBJECT_DETECTION) {
				objectDetectionDomain = domain;
				break;
			}
		}
		return objectDetectionDomain;
	}

	/**
	 * Creates a tag on Azure Project 
	 */
	public static Tag createTag(String tagName) {
		logger.info("Creating tag: " + tagName);
		Tag newTag = trainer.createTag().withProjectId(project.id()).withName(tagName).execute();
		tagList = trainer.getTags().withProjectId(project.id()).execute();
		return newTag;
	}
	
	public static Tag getTagByName(String tagName) {
		for (Tag currentTag : tagList) {
			if (currentTag.name().equals(tagName)) {
				return currentTag;
			} 
		}
		return createTag(tagName);
	}

	/**
	 * Lists all XML files on a given directory 
	 */
	public static File[] listAllXMLs(String dir) {
		final File folder = new File(dir);

		File[] files = folder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				// TODO Auto-generated method stub
				return name.toLowerCase().endsWith(".xml");
			}
		});

		return files;
	}
	
	
	
	/**
	 * Adds image to the Image List
	 */
	public static void addImageToList(ImageDescriptor img) {
		try {
			logger.info("Loading image: " + img);
			
			ArrayList<ObjectTag> tagList = img.getTagList();
			ArrayList<Region> regions = new ArrayList();

			for (ObjectTag currentTag : tagList) {
				// Create a region with a tag
				Region region = new Region()
						.withTagId(getTagByName(currentTag.getName()).id())
						.withLeft(currentTag.getXmin())
						.withTop(currentTag.getYmin())
						.withWidth(currentTag.getXmax()-currentTag.getXmin())  	// Azure expects width instead of XMax 
						.withHeight(currentTag.getYmax()-currentTag.getYmin());	// Azure expects height instead of YMax
				
				regions.add(region);
				logger.info("Region: " + currentTag.getName() + " - (" + currentTag.getXmin() + ", " + currentTag.getYmin() +") - (" + currentTag.getXmax() + ", " + currentTag.getYmax() + ")");				
				
			}

			// Creates a list of images to be uploaded as a batch on Azure
			ImageFileCreateEntry imgFile = new ImageFileCreateEntry().withName(img.getFileName()).withContents(img.getFileContents())
					.withRegions(regions);
			
			imgList.add(imgFile);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add a batch of images to a Project
	 */
	public static void addImageListToProject() {
		ImageFileCreateBatch batch = new ImageFileCreateBatch().withImages(imgList);
		logger.info("Uploading image batch...");
		trainer.createImagesFromFiles(project.id(), batch); // Upload images to Azure
		logger.info("Uploaded!");
		

	}
	
	public static void train() {
		logger.info("Training...");
		TrainProjectOptionalParameter params = new TrainProjectOptionalParameter();
		iteration = trainer.trainProject(project.id(), new TrainProjectOptionalParameter());
		while (iteration.status().equals("Training"))
        {
			logger.info("Training Status: "+ iteration.status());
            try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
            iteration = trainer.getIteration(project.id(), iteration.id());
            
        }

		logger.info("Training Status: "+ iteration.status());

		logger.info("Publishing Iteration..");

        String predictionResourceId = System.getenv("AZURE_CUSTOMVISION_PREDICTION_ID");
        trainer.publishIteration(project.id(), iteration.id(), publishedModelName, predictionResourceId);
        logger.info("Iteration published! Name: " + publishedModelName + ", ID: " + iteration.id());
        
	
	}
	
	public static void uploadTestImages(String dir) {
		logger.info("Uploading Test Images");
		
		logger.info("Source: " + dir);
		final File folder = new File(dir);
		File[] files = folder.listFiles();
		
		for (File file:files) {
			try {
				logger.info("Predictions for " + file.getName());
				byte[] testImage = Files.readAllBytes(file.toPath());
				
				
				
/*				ImagePrediction results = predictClient.predictions().classifyImage()
		                .withProjectId(project.id())
		                .withPublishedName(publishedModelName)
		                .withImageData(testImage)
		                .execute();*/
				//ImagePrediction results = predictClient.predictions().detectImage(project.id(), publishedModelName, testImage, new DetectImageOptionalParameter());
				ImagePrediction results = predictClient.predictions()
						.detectImage()
						.withProjectId(project.id())
						.withPublishedName(publishedModelName)
						.withImageData(testImage)
						.execute();
				
				
				for (Prediction prediction: results.predictions())
	            {
	                System.out.println(String.format("\t%s: %.2f%%", prediction.tagName(), prediction.probability() * 100.0f));
	            }
				
			} catch (IOException e) {
				// TODO Auto-generated catch block
				e.printStackTrace();
			}
		}
		
>>>>>>> 63b65947f43f217fe1651be2dc2a2e3fd2894d62
	}



}
