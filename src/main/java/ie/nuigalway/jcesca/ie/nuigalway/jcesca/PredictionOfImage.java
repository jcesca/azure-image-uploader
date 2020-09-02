package ie.nuigalway.jcesca.ie.nuigalway.jcesca;

import java.io.File;

import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.ImagePrediction;

public class PredictionOfImage {

	ImagePrediction predictions;
	File file;
	ImageDescriptor imageDescriptor;

	public ImageDescriptor getImageDescriptor() {
		return imageDescriptor;
	}

	public void setImageDescriptor(ImageDescriptor imageDescriptor) {
		this.imageDescriptor = imageDescriptor;
	}

	public PredictionOfImage(File file, ImagePrediction predictions, ImageDescriptor imageDescriptor) {
		this.predictions = predictions;
		this.file = file;
		this.imageDescriptor = imageDescriptor;
	}

	public ImagePrediction getPredictions() {
		return predictions;
	}

	public void setPredictions(ImagePrediction predictions) {
		this.predictions = predictions;
	}

	public File getFile() {
		return file;
	}

	public void setFile(File file) {
		this.file = file;
	}
	
	

}
