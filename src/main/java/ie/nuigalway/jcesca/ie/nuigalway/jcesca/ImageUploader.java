package ie.nuigalway.jcesca.ie.nuigalway.jcesca;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.CustomVisionPredictionClient;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.CustomVisionPredictionManager;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.ImagePrediction;
import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.CustomVisionTrainingClient;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.CustomVisionTrainingManager;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.Trainings;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Classifier;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Domain;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.DomainType;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.GetIterationPerformanceOptionalParameter;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.ImageFileCreateBatch;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.ImageFileCreateEntry;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Iteration;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.IterationPerformance;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Project;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Region;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.Tag;
import com.microsoft.azure.cognitiveservices.vision.customvision.training.models.TrainProjectOptionalParameter;

public class ImageUploader {

	// Read Environment Variables
	private final Logger logger;

	// Class Variables
	private Project project;
	private Trainings trainerClient;
	private CustomVisionPredictionClient predictClient;
	private List<Tag> tagList;
	private ArrayList<ImageFileCreateEntry> imgList = new ArrayList<ImageFileCreateEntry>();
	private Iteration iteration = null;
	private final int maxUploadBatchSize = 20;
	private double TP;
	private double FP;
	private double FN;

	public ImageUploader() {
		this.logger = LoggerFactory.getLogger(ImageUploader.class);
	}

	/**
	 * Connects to Azure using Endpoint and trainingApiKey and predictionApiKey.
	 * Stores Trainer Client and PredictClient
	 */
	public void connect(String endpoint, String trainingApiKey, String predictionApiKey) {
		try {
			// Authenticate using Azure API - Trainer
			logger.info("Connecting Train Client");
			CustomVisionTrainingClient trainClient = CustomVisionTrainingManager
					.authenticate("https://" + endpoint + "/customvision/v3.0/training/", trainingApiKey)
					.withEndpoint(endpoint);
			trainerClient = trainClient.trainings();

			// Authenticate using Azure API - Predictor
			logger.info("Connecting Prediction Client");
			predictClient = CustomVisionPredictionManager
					.authenticate("https://{Endpoint}/customvision/v3.0/prediction/", predictionApiKey)
					.withEndpoint(endpoint);
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Loads an already created project by its ID
	 */
	public void loadProjectById(String id) {
		this.project = this.trainerClient.getProject(UUID.fromString(id));
	}

	/**
	 * Creates project on Azure
	 */
	public void createProject(String projectName, String projectDescription) {
		// Create a Project on Azure (customvision.ai)
		logger.info("Creating project...");
		this.project = this.trainerClient.createProject().withName(projectName) // Project Name
				.withDescription(projectDescription).withDomainId(getObjectDetectionDomain().id()) // With Object
																									// Detection Domain
																									// set
				.withClassificationType(Classifier.MULTILABEL.toString()).execute();

		// TODO: check why using MULTILABEL

		// Store the tagList
		this.tagList = this.trainerClient.getTags().withProjectId(this.project.id()).execute();

		logger.info("Project " + projectName + " created with ID: " + this.project.id());

	}

	/**
	 * Finds and returns the Object Detection domain
	 */
	public Domain getObjectDetectionDomain() {
		// Get the Object Detection Domain
		List<Domain> domains = this.trainerClient.getDomains();
		for (final Domain domain : domains) {
			if (domain.type() == DomainType.OBJECT_DETECTION) {
				return domain;
			}
		}
		return null;
	}

	/**
	 * Gets a Tag by its Name or create a new one
	 */
	public Tag getTagByName(String tagName) {
		for (Tag currentTag : this.tagList) {
			if (currentTag.name().equals(tagName)) {
				return currentTag;
			}
		}
		return createTag(tagName);
	}

	/**
	 * Creates a tag on Azure Project
	 */
	public Tag createTag(String tagName) {
		logger.info("Creating tag: " + tagName);
		Tag newTag = this.trainerClient.createTag().withProjectId(this.project.id()).withName(tagName).execute();
		this.tagList = this.trainerClient.getTags().withProjectId(this.project.id()).execute();
		return newTag;
	}

	/**
	 * Lists all XML files on a given directory
	 */
	public File[] listAllXMLs(String dir) {
		final File folder = new File(dir);

		File[] files = folder.listFiles(new FilenameFilter() {
			@Override
			public boolean accept(File dir, String name) {
				return name.toLowerCase().endsWith(".xml");
			}
		});

		return files;
	}

	/**
	 * Load images from Files into Azure
	 */
	public void loadImagesFromFiles(String directory) {
		File[] xmlList = listAllXMLs(directory);

		int batch = 1;
		for (File f : xmlList) {
			ImageDescriptor imgDesc = new ImageDescriptor(directory, f.getName());
			addImageToList(imgDesc);
			if (imgList.size() == maxUploadBatchSize) {
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
	}

	/**
	 * Adds image to the Image List
	 */
	public void addImageToList(ImageDescriptor img) {
		try {
			NumberFormat nf = NumberFormat.getInstance();

			ArrayList<ObjectTag> tagList = img.getTagList();
			ArrayList<Region> regions = new ArrayList<Region>();

			for (ObjectTag currentTag : tagList) {
				// Create a region with a tag
				Region region = new Region().withTagId(getTagByName(currentTag.getName()).id())
						.withLeft(currentTag.getXmin()).withTop(currentTag.getYmin())
						.withWidth(currentTag.getXmax() - currentTag.getXmin()) // Azure expects width instead of XMax
						.withHeight(currentTag.getYmax() - currentTag.getYmin()); // Azure expects height instead of
																					// YMax

				regions.add(region);
				logger.info("Image Loaded: " + img + " - Region: " + currentTag.getName() + " - ("
						+ nf.format(currentTag.getXmin()) + ", " + nf.format(currentTag.getYmin()) + ")-("
						+ nf.format(currentTag.getXmax()) + ", " + nf.format(currentTag.getYmax()) + ")");

			}

			// Creates a list of images to be uploaded as a batch on Azure
			ImageFileCreateEntry imgFile = new ImageFileCreateEntry().withName(img.getFileName())
					.withContents(img.getFileContents()).withRegions(regions);

			this.imgList.add(imgFile);

		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	/**
	 * Add a batch of images to a Project
	 */
	public void addImageListToProject() {
		ImageFileCreateBatch batch = new ImageFileCreateBatch().withImages(this.imgList);
		logger.info("Uploading image batch...");
		this.trainerClient.createImagesFromFiles(this.project.id(), batch); // Upload images to Azure
		logger.info("Uploaded!");

	}

	/**
	 * Triggers a training on Azure
	 */
	public void train(String modelName) {
		logger.info("Training Started!");
		this.iteration = trainerClient.trainProject(this.project.id(), new TrainProjectOptionalParameter());
		while (this.iteration.status().equals("Training")) {
			logger.info("Training Status: " + this.iteration.status());
			try {
				Thread.sleep(5000);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			this.iteration = trainerClient.getIteration(this.project.id(), this.iteration.id());
		}

		logger.info("Training Status: " + this.iteration.status());

		logger.info("Publishing Iteration..");

		String predictionResourceId = System.getenv("AZURE_CUSTOMVISION_PREDICTION_ID");
		this.trainerClient.publishIteration(this.project.id(), this.iteration.id(), modelName, predictionResourceId);

		logger.info("Training Finished! Iteration published with Name: " + modelName + ", ID: " + this.iteration.id());
		this.printIterationPerformance();

	}

	/**
	 * Upload Test Images
	 */

	public void uploadTestImages(String dir, String modelName) {
		logger.info("Uploading Test Images");

		logger.info("Source: " + dir);
		final File folder = new File(dir);
		File[] files = folder.listFiles();

		for (File file : files) {
			if (!file.getName().endsWith(".xml")) {
				String xmlName = file.getName().substring(0, file.getName().length() - 3) + "xml";
				logger.info("XML: " + xmlName);
				ImageDescriptor imgDesc = new ImageDescriptor(dir, xmlName);

				try {
					// logger.info("Uploading" + file.getName());
					byte[] testImage = Files.readAllBytes(file.toPath());

					ImagePrediction results = this.predictClient.predictions().detectImage()
							.withProjectId(this.project.id()).withPublishedName(modelName).withImageData(testImage)
							.execute();

					logger.info("Uploaded " + file.getName() + " - " + results.predictions().size() + " predictions.");
					boolean foundPredictionAboveTreshold = false;

					for (Prediction p : results.predictions()) {
						double probability = p.probability();
						if (probability >= .15) {
							foundPredictionAboveTreshold = true;

							double LeftA = p.boundingBox().left();
							double TopA = p.boundingBox().top();
							double RightA = p.boundingBox().left() + p.boundingBox().width();
							double BottomA = p.boundingBox().top() + p.boundingBox().height();

							Box boxA = new Box(TopA, BottomA, LeftA, RightA);

							for (ObjectTag t : imgDesc.getTagList()) {
								// logger.info(t.toString());
								double LeftB = t.getXmin();
								double TopB = t.getYmin();
								double RightB = t.getXmax();
								double BottomB = t.getYmax();

								Box boxB = new Box(TopB, BottomB, LeftB, RightB);
								double IoU = boxA.getIoU(boxB);
								this.processIoU(IoU);

								logger.info(p.tagName() + ": " + (probability * 100) + "% " + boxA + " Area: "
										+ boxA.getArea() + " Overlap: " + boxA.doOverlap(boxB) + " iArea: "
										+ boxA.getIntersectionArea(boxB) + " IoU: " + IoU);
							}
						}

					}
					
					if (!foundPredictionAboveTreshold) {
						this.FN++;
					}

				} catch (IOException e) {
					e.printStackTrace();
				}

			}
		}

	}

	public void processIoU(double IoU) {
		if (IoU >= .5) {
			this.TP++;
		} else {
			this.FP++;
		}
	}

	public void unpublishModel() {
		logger.info("Unpublishing Iteration...");
		this.trainerClient.unpublishIteration(project.id(), iteration.id());
	}

	public void deleteProject() {
		logger.info("Deleting Project...");
		this.trainerClient.deleteProject(project.id());
	}

	public void printIterationPerformance() {
		IterationPerformance ip = trainerClient.getIterationPerformance(project.id(), iteration.id(),
				new GetIterationPerformanceOptionalParameter());
		logger.info("Precision: " + ip.precision());
		logger.info("Recall: " + ip.recall());
		logger.info("mAP: " + ip.averagePrecision());
	}

	public void printMetrics() {
		logger.info("Model Metrics:");
		logger.info("TP: " + this.TP);
		logger.info("FP: " + this.FP);
		logger.info("FN: " + this.FN);
		logger.info("Accuracy: " + this.getAccuracy());
		logger.info("Precision: " + this.getPrecision());
		logger.info("Recall: " + this.getRecall());
	}

	public String getMetrics(String modelName) {
		IterationPerformance ip = trainerClient.getIterationPerformance(project.id(), iteration.id(),
				new GetIterationPerformanceOptionalParameter());
		return modelName + ", " + this.TP + ", " + this.FP + ", " + this.FN + ", " + this.getAccuracy() + ", "
				+ this.getPrecision() + ", " + this.getRecall() + ", " + ip.precision() + ", " + ip.recall() + ", "
				+ ip.averagePrecision();
	}

	public void saveMetrics(String modelName) {

		logger.info("Writing to file..");
		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter("metrics.txt", true) // Set true for append mode
			);
			writer.newLine(); // Add new line
			writer.write(this.getMetrics(modelName));
			writer.close();
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	public double getAccuracy() {
		return (TP / (TP + FP + FN));
	}

	public double getPrecision() {
		return (TP / (TP + FP));
	}

	public double getRecall() {
		return (TP / (TP + FN));
	}
}
