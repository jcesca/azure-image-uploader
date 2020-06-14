package ie.nuigalway.jcesca.ie.nuigalway.jcesca;

public class ObjectTag {

	private double xmin;
	private double ymin;
	private double xmax;
	private double ymax;
	private String name;


	public double getXmin() {
		return xmin;
	}

	public void setXmin(double xmin) {
		this.xmin = xmin;
	}

	public double getYmin() {
		return ymin;
	}

	public void setYmin(double ymin) {
		this.ymin = ymin;
	}

	public double getXmax() {
		return xmax;
	}

	public void setXmax(double xmax) {
		this.xmax = xmax;
	}

	public double getYmax() {
		return ymax;
	}

	public void setYmax(double ymax) {
		this.ymax = ymax;
	}

	public String getName() {
		return name;
	}

	public void setName(String name) {
		this.name = name;
	}
	
	public String toString() {
		return name + ": (" + xmin + ", " + ymin + ")-(" + xmax + ", " + ymax + ")";
		
	}

}
