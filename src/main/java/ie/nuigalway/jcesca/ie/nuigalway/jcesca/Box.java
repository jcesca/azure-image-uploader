package ie.nuigalway.jcesca.ie.nuigalway.jcesca;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.microsoft.azure.cognitiveservices.vision.customvision.prediction.models.Prediction;

public class Box {

	private double top, bottom, left, right = 0;
	private final Logger logger;

	public Box(double top, double bottom, double left, double right) {
		this.top = top;
		this.bottom = bottom;
		this.left = left;
		this.right = right;
		this.logger = LoggerFactory.getLogger(Box.class);
	}
	
	public static Box getBox(Prediction p) {
		double predLeft = p.boundingBox().left();
		double predTop = p.boundingBox().top();
		double predRight = p.boundingBox().left() + p.boundingBox().width();
		double predBottom = p.boundingBox().top() + p.boundingBox().height();
		return new Box(predTop, predBottom, predLeft, predRight);
	}

	public double getTop() {
		return top;
	}

	public void setTop(double top) {
		this.top = top;
	}

	public double getBottom() {
		return bottom;
	}

	public void setBottom(double bottom) {
		this.bottom = bottom;
	}

	public double getLeft() {
		return left;
	}

	public void setLeft(double left) {
		this.left = left;
	}

	public double getRight() {
		return right;
	}

	public void setRight(double right) {
		this.right = right;
	}

	public boolean doOverlap(Box b) {
		// if one box is at the side of another
		/*logger.info("This.right: " + this.right);
		logger.info("This.left: " + this.left);
		logger.info("b.right: " + b.right);
		logger.info("b.left: " + b.left);*/
		
		if ((this.right < b.left) || (this.left > b.right)) {
			return false;
		}

		// if on box is on the top of another
		/*logger.info("This.top: " + this.top);
		logger.info("This.bottom: " + this.bottom);
		logger.info("b.top: " + b.top);
		logger.info("b.bottom: " + b.bottom);*/
		if ((this.bottom < b.top) || (this.top > b.bottom)) {
			return false;
		}

		return true;
	}

	public double getArea() {
		// base x height
		return (right - left) * (bottom - top);
	}

	public double getIntersectionArea(Box b) {
		if (this.doOverlap(b)) {
			double base = Math.min(this.right, b.right) - Math.max(this.left, b.left);
			double height = Math.min(this.bottom, b.bottom) - Math.max(this.top, b.top);
			return (base * height);
		} else {
			return 0;
		}
	}

	public double getIoU(Box b) {
		double intersection = getIntersectionArea(b);
		double union = this.getArea() + b.getArea() - intersection;
		return (intersection/union);
	}
	
	public String toString() {
		return "(" + left + ", " + top + ")-(" + right + ", " + bottom + ")";
	}

}
