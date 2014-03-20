package edu.cmu.scs.azurite.model;

import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * @author YoungSeok Yoon
 * 
 * A FileKey class is composed of a project name and a file name.
 */
public class FileKey {
	
	private final String mProjectName;
	private final Path mFilePath;
	
	public FileKey(String projectName, String filePath) {
		mProjectName = projectName;
		mFilePath = filePath == null ? null : Paths.get(filePath);
	}
	
	public String getProjectName() {
		return mProjectName;
	}
	
	public String getFilePath() {
		return mFilePath == null ? null : mFilePath.toString();
	}
	
	public String getFileNameOnly() {
		String filePath = getFilePath();
		return filePath == null ? null : Paths.get(filePath).getFileName().toString();
	}

	@Override
	public int hashCode() {
		final int prime = 31;
		int result = 1;
		result = prime * result
				+ ((mFilePath == null) ? 0 : mFilePath.hashCode());
		result = prime * result
				+ ((mProjectName == null) ? 0 : mProjectName.hashCode());
		return result;
	}

	@Override
	public boolean equals(Object obj) {
		if (this == obj) {
			return true;
		}
		
		if (obj == null) {
			return false;
		}
		
		if (getClass() != obj.getClass()) {
			return false;
		}
		
		FileKey other = (FileKey) obj;
		if (mFilePath == null) {
			if (other.mFilePath != null) {
				return false;
			}
		} else if (!mFilePath.equals(other.mFilePath)) {
			return false;
		}
		
		if (mProjectName == null) {
			if (other.mProjectName != null) {
				return false;
			}
		} else if (!mProjectName.equals(other.mProjectName)) {
			return false;
		}
		
		return true;
	}

	@Override
	public String toString() {
		return "ProjectName: " + getProjectName() + "\tFilePath: " + getFilePath();
	}
	
}