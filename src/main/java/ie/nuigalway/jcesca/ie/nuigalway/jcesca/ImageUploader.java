package ie.nuigalway.jcesca.ie.nuigalway.jcesca;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.lang.reflect.Array;
import java.nio.file.Files;
import java.text.NumberFormat;
import java.util.ArrayList;
import java.util.Arrays;
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
	/*
	 * private double TP; private double FP; private double FN;
	 */

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
		// List all XMLs
		File[] xmlList = listAllXMLs(directory);

		// Batch counter (for logging only)
		int batch = 1;
		
		// Load Images to batch Array
		for (File f : xmlList) {
			ImageDescriptor imgDesc = new ImageDescriptor(directory, f.getName());
			addImageToList(imgDesc);
			if (imgList.size() == maxUploadBatchSize) {
				logger.info("Uploading Batch: " + batch);
				// Add images to Project
				addImageListToProject();
				imgList = new ArrayList<ImageFileCreateEntry>();
				batch++;
			}
		}
		if (imgList.size() > 0) {
			// Finally, add any remaining images to the project 
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
		try {
			while (this.iteration.status().equals("Training")) {
				logger.info("Training Status: " + this.iteration.status());
				Thread.sleep(5000);
				this.iteration = trainerClient.getIteration(this.project.id(), this.iteration.id());
			}
		} catch (Exception e) {
			e.printStackTrace();
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

		ArrayList<PredictionOfImage> imgPredList = new ArrayList<PredictionOfImage>();

		for (File file : files) {

			String fileName = file.getName();

			if (!fileName.endsWith(".xml")) {
				logger.info("Uploading Test File: " + fileName);

				String fileNameNoExtension = fileName.substring(0, fileName.length() - 4);
				String xmlName = fileNameNoExtension + ".xml";
				ImageDescriptor imgDesc = new ImageDescriptor(dir, xmlName);

				try {
					// logger.info("Uploading" + file.getName());
					byte[] testImage = Files.readAllBytes(file.toPath());

					ImagePrediction predictions = this.predictClient.predictions().detectImage()
							.withProjectId(this.project.id()).withPublishedName(modelName).withImageData(testImage)
							.execute();

					PredictionOfImage predImage = new PredictionOfImage(file, predictions, imgDesc);

					imgPredList.add(predImage);

					logger.info(
							"Uploaded " + file.getName() + " - " + predictions.predictions().size() + " predictions.");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		validateAllThresholds(imgPredList, modelName);

	}

	public void validateAllThresholds(ArrayList<PredictionOfImage> imgPredList, String modelName) {
		for (double confidenceThreshold = .1; confidenceThreshold < 1; confidenceThreshold = confidenceThreshold + .1) {
			calculateMetrics(imgPredList, confidenceThreshold, modelName);
		}
	}

	public void uploadTestImages_old(String dir, String modelName) {
		logger.info("Uploading Test Images");

		logger.info("Source: " + dir);
		final File folder = new File(dir);
		File[] files = folder.listFiles();

		ArrayList<PredictionOfImage> imgPredList = new ArrayList<PredictionOfImage>();

		for (File file : files) {

			String fileName = file.getName();

			if (!fileName.endsWith(".xml")) {
				logger.info("Uploading Test File: " + fileName);

				String fileNameNoExtension = fileName.substring(0, fileName.length() - 4);
				String xmlName = fileNameNoExtension + ".xml";
				ImageDescriptor imgDesc = new ImageDescriptor(dir, xmlName);

				try {
					// logger.info("Uploading" + file.getName());
					byte[] testImage = Files.readAllBytes(file.toPath());

					ImagePrediction predictions = this.predictClient.predictions().detectImage()
							.withProjectId(this.project.id()).withPublishedName(modelName).withImageData(testImage)
							.execute();

					PredictionOfImage predImage = new PredictionOfImage(file, predictions, imgDesc);

					imgPredList.add(predImage);

					logger.info(
							"Uploaded " + file.getName() + " - " + predictions.predictions().size() + " predictions.");
				} catch (IOException e) {
					e.printStackTrace();
				}
			}
		}

		for (double threshold = .1; threshold < 1; threshold = threshold + .1) {

			double TP = 0;
			double FP = 0;
			double FN = 0;

			// For each image
			for (PredictionOfImage predImage : imgPredList) {
				ImageDescriptor imgDesc = predImage.getImageDescriptor();

				// For each Prediction
				for (Prediction p : predImage.getPredictions().predictions()) {
					double probability = p.probability();

					// For each prediction with an acceptable probability
					if (probability >= threshold) {

						double predLeft = p.boundingBox().left();
						double predTop = p.boundingBox().top();
						double predRight = p.boundingBox().left() + p.boundingBox().width();
						double predBottom = p.boundingBox().top() + p.boundingBox().height();

						Box predBox = new Box(predTop, predBottom, predLeft, predRight);

						// For each True Tag
						for (ObjectTag t : imgDesc.getTagList()) {

							double trueLeft = t.getXmin();
							double trueTop = t.getYmin();
							double trueRight = t.getXmax();
							double trueBottom = t.getYmax();

							Box trueBox = new Box(trueTop, trueBottom, trueLeft, trueRight);

							double IoU = predBox.getIoU(trueBox);
							if (IoU > 0.5) {
								// True Detection
								TP++;
							} else {
								// False Detection
								FP++;
							}

						}

					} else {
						// False detection, below confidence threshold
						FP++;
					}
				}

				// Find False Negatives
				// For each tag
				for (ObjectTag t : imgDesc.getTagList()) {
					boolean detectedTag = false;

					double trueLeft = t.getXmin();
					double trueTop = t.getYmin();
					double trueRight = t.getXmax();
					double trueBottom = t.getYmax();

					Box trueBox = new Box(trueTop, trueBottom, trueLeft, trueRight);

					for (Prediction p : predImage.getPredictions().predictions()) {
						double probability = p.probability();

						// For each prediction with an acceptable probability
						if (probability >= threshold) {

							double predLeft = p.boundingBox().left();
							double predTop = p.boundingBox().top();
							double predRight = p.boundingBox().left() + p.boundingBox().width();
							double predBottom = p.boundingBox().top() + p.boundingBox().height();

							Box predBox = new Box(predTop, predBottom, predLeft, predRight);

							double IoU = predBox.getIoU(trueBox);

							if (IoU > .5) {
								detectedTag = true;
							}

						}
					}

					if (!detectedTag) {
						// False negative (true tag not detected)
						FN++;
					}

				}

			}
			saveMetricsByModel(modelName, threshold, TP, FP, FN);
		}

	}

	public void calculateMetrics(ArrayList<PredictionOfImage> imgPredList, double confidenceThreshold,
			String modelName) {

		/*
		 * How predictions work: - When multiple boxes detect the same object, the box
		 * with the highest IoU is considered TP, while the remaining boxes are
		 * considered FP. - If the object is present and the predicted box has an IoU <
		 * threshold with ground truth box, The prediction is considered FP. More
		 * importantly, because no box detected it properly, the class object receives
		 * FN, . - If the object is not in the image, yet the model detects one then the
		 * prediction is considered FP. - Recall and precision are then computed for
		 * each class by applying the above-mentioned formulas, where predictions of TP,
		 * FP and FN are accumulated.
		 */
		int TP = 0; // True Positive - correct detection of an existing object with IoU above
					// threshold (highest IoU only)
		int FP = 0; // False Positive - correct detection below IoU threshold or a detection of an
					// area that doesn't belong to an object
		int FN = 0; // False Negative - non-detection of an existing object
		double IoUThreshold = .5;

		// For each image
		for (PredictionOfImage imgPred : imgPredList) {
			ImageDescriptor imgDesc = imgPred.getImageDescriptor();
			ImagePrediction predList = imgPred.getPredictions();

			// When multiple boxes detect the same object, the box with the highest IoU is
			// considered TP, while the remaining boxes are considered FP.

			for (ObjectTag t : imgDesc.getTagList()) {
				Box trueBox = t.getBox();

				double bestIoU = 0;

				for (Prediction p : predList.predictions()) {
					if (p.probability() >= confidenceThreshold) {

						Box predBox = Box.getBox(p);

						double IoU = trueBox.getIoU(predBox);

						if (IoU > 0) {
							if (IoU > IoUThreshold) {
								if (bestIoU == 0) {
									bestIoU = IoU; // First detection that found the right object
								} else if (IoU > bestIoU) {
									bestIoU = IoU; // Change to a better detection
									FP++; // while the remaining boxes are considered FP.
								}

							} else {
								FP++; // Object detected with an IoU below the threshold
							}

						}

					}
				}

				if (bestIoU == 0) {
					// Object was not found
					FN++;
				} else {
					// There was one good detection
					TP++;
				}
			}

			// False Negatives where there is a prediction on an area without any object.

			for (Prediction p : predList.predictions()) {
				if (p.probability() >= confidenceThreshold) {
					Box predBox = Box.getBox(p);
					boolean hasIntersection = false;
					for (ObjectTag t : imgDesc.getTagList()) {
						Box trueBox = t.getBox();
						double IoU = trueBox.getIoU(predBox);
						if (IoU > 0) {
							hasIntersection = true;
						}
					}
					if (!hasIntersection) {
						// False detection. There is a prediction on an area without any object
						FP++;
					}
				}
			}
		}

		saveMetricsByModel(modelName, confidenceThreshold, TP, FP, FN);
	}

	/*
	 * public void uploadTestImages_new(String dir, String modelName) {
	 * logger.info("Uploading Test Images");
	 * 
	 * logger.info("Source: " + dir); final File folder = new File(dir); File[]
	 * files = folder.listFiles();
	 * 
	 * for (File file : files) { if (!file.getName().endsWith(".xml")) {
	 * 
	 * // Load Test Image True tags from XML String xmlName =
	 * file.getName().substring(0, file.getName().length() - 3) + "xml"; String
	 * fileNameNoExtension = xmlName.substring(0, xmlName.length() - 4);
	 * logger.info("XML: " + xmlName); logger.info("NoExtensionName: " +
	 * fileNameNoExtension); ImageDescriptor imgDesc = new ImageDescriptor(dir,
	 * xmlName);
	 * 
	 * for (ObjectTag t : imgDesc.getTagList()) { // True tag box from Test Image
	 * XML double LeftB = t.getXmin(); double TopB = t.getYmin(); double RightB =
	 * t.getXmax(); double BottomB = t.getYmax(); Box boxB = new Box(TopB, BottomB,
	 * LeftB, RightB);
	 * 
	 * // Upload test image to Azure and collect predictions as results byte[]
	 * testImage; try { testImage = Files.readAllBytes(file.toPath());
	 * ImagePrediction results = this.predictClient.predictions().detectImage()
	 * .withProjectId(this.project.id()).withPublishedName(modelName).withImageData(
	 * testImage) .execute();
	 * 
	 * // Iterate through predictions if (results.predictions().size() > 0) { for
	 * (Prediction p : results.predictions()) { double probability =
	 * p.probability(); double LeftA = p.boundingBox().left(); double TopA =
	 * p.boundingBox().top(); double RightA = p.boundingBox().left() +
	 * p.boundingBox().width(); double BottomA = p.boundingBox().top() +
	 * p.boundingBox().height();
	 * 
	 * Box boxA = new Box(TopA, BottomA, LeftA, RightA); double IoU =
	 * boxA.getIoU(boxB); saveModelDetailedMetrics(modelName, fileNameNoExtension,
	 * boxB, p, true, IoU, probability); }
	 * 
	 * } else { // FN saveModelDetailedMetrics(modelName, fileNameNoExtension, boxB,
	 * null, false, 0, 0); }
	 * 
	 * } catch (IOException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); }
	 * 
	 * 
	 * }
	 * 
	 * } }
	 * 
	 * }
	 */

	/*
	 * public void processIoU(double IoU) { if (IoU >= .5) { this.TP++; } else {
	 * this.FP++; } }
	 */

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

	/*
	 * public void printMetrics() { logger.info("Model Metrics:");
	 * logger.info("TP: " + this.TP); logger.info("FP: " + this.FP);
	 * logger.info("FN: " + this.FN); logger.info("Accuracy: " +
	 * this.getAccuracy()); logger.info("Precision: " + this.getPrecision());
	 * logger.info("Recall: " + this.getRecall()); }
	 * 
	 * public String getMetrics(String modelName) { IterationPerformance ip =
	 * trainerClient.getIterationPerformance(project.id(), iteration.id(), new
	 * GetIterationPerformanceOptionalParameter()); return modelName + ", " +
	 * this.TP + ", " + this.FP + ", " + this.FN + ", " + this.getAccuracy() + ", "
	 * + this.getPrecision() + ", " + this.getRecall() + ", " + ip.precision() +
	 * ", " + ip.recall() + ", " + ip.averagePrecision(); }
	 */

	/*
	 * public void saveModelDetailedMetrics(String modelName, String imageName, Box
	 * trueBox, Prediction prediction, boolean found, double IoU, double
	 * probability) { logger.info("Writing to file.."); BufferedWriter writer; //
	 * Get build the content using collected data StringBuilder content = new
	 * StringBuilder(); content.append(modelName + ", "); content.append(imageName +
	 * ", "); content.append(trueBox.hashCode() + ", ");
	 * content.append(prediction.hashCode() + ", "); content.append(found + ", ");
	 * content.append(IoU + ", "); content.append(probability);
	 * 
	 * try { writer = new BufferedWriter(new FileWriter(modelName + ".txt", true));
	 * writer.newLine(); // Add new line writer.write(content.toString());
	 * writer.close(); } catch (IOException e) { // TODO Auto-generated catch block
	 * e.printStackTrace(); }
	 * 
	 * }
	 */
	public void saveMetricsByModel(String modelName, double threshold, double tp, double fp, double fn) {

		logger.info("Writing to file..");

		double accuracy = ((tp) / (tp + fp + fn));
		double precision = ((tp) / (tp + fp));
		double recall = ((tp) / (tp + fn));
		double fOneScore = (2 * (precision * recall) / (precision + recall));

		StringBuilder metrics = new StringBuilder();
		metrics.append(threshold + ",");
		metrics.append(fp + ",");
		metrics.append(tp + ",");
		metrics.append(fn + ",");
		metrics.append(accuracy + ",");
		metrics.append(recall + ",");
		metrics.append(precision + ",");
		metrics.append(fOneScore);
		metrics.append("\r\n");

		BufferedWriter writer;
		try {
			writer = new BufferedWriter(new FileWriter(modelName + ".txt", true) // Set true for append mode
			);
			writer.write(metrics.toString());
			writer.close();
			logger.info(metrics.toString());
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}

	/*
	 * public void saveMetrics(String modelName) {
	 * 
	 * logger.info("Writing to file.."); BufferedWriter writer; try { writer = new
	 * BufferedWriter(new FileWriter("metrics.txt", true) // Set true for append
	 * mode ); writer.newLine(); // Add new line
	 * writer.write(this.getMetrics(modelName)); writer.close(); } catch
	 * (IOException e) { // TODO Auto-generated catch block e.printStackTrace(); } }
	 */
	/*
	 * public double getAccuracy() { return (TP / (TP + FP + FN)); }
	 * 
	 * public double getPrecision() { return (TP / (TP + FP)); }
	 * 
	 * public double getRecall() { return (TP / (TP + FN)); }
	 */
}
