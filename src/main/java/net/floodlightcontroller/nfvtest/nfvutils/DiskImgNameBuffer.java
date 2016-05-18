package net.floodlightcontroller.nfvtest.nfvutils;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedList;

public class DiskImgNameBuffer {
	private ArrayList<LinkedList<String>> dpDiskImgNameBuffer;
	private ArrayList<LinkedList<String>> cpDiskImgNameBuffer;
	private HashMap<String, Integer> nameMap;
	
	public DiskImgNameBuffer(){
		this.dpDiskImgNameBuffer = new ArrayList<LinkedList<String>>();
		for(int i=0; i<3; i++){
			LinkedList<String> ll = new LinkedList<String>();
			this.dpDiskImgNameBuffer.add(ll);
		}
		
		this.cpDiskImgNameBuffer = new ArrayList<LinkedList<String>>();
		for(int i=0; i<2; i++){
			LinkedList<String> ll = new LinkedList<String>();
			this.cpDiskImgNameBuffer.add(ll);
		}
		
		this.nameMap = new HashMap<String, Integer>();
	}
	
	public void load(){
		File folder = new File("/root/nfvenv/img/");
		File[] listOfFiles = folder.listFiles();
		
		for (int i = 0; i < listOfFiles.length; i++) {
			if (listOfFiles[i].isFile()) {
				String fileName = listOfFiles[i].getName();
				if(fileName.contains("CONTROL") || fileName.contains("DATA")){
					String[] stringSplit = fileName.split("-");
					String whichPlane = stringSplit[0];
					int stageIndex = new Integer(stringSplit[1]).intValue();
					if(whichPlane.equals("CONTROL")){
						System.out.println(fileName+" is added to stage "+new Integer(stageIndex).toString()+" cp buffer.");
						cpDiskImgNameBuffer.get(stageIndex).push(fileName);
					}
					else{
						System.out.println(fileName+" is added to stage "+new Integer(stageIndex).toString()+" dp buffer.");
						dpDiskImgNameBuffer.get(stageIndex).push(fileName);
					}
					this.nameMap.put(fileName, 0);
				}
			}
	    }
	}
	
	public String getDiskImgName(String chainName, int stageIndex){
		if(chainName.equals("DATA")){
			if(this.dpDiskImgNameBuffer.get(stageIndex).size()>0){
				String diskImgName = this.dpDiskImgNameBuffer.get(stageIndex).pop();
				return diskImgName;
			}
			else{
				return "noAvailableDiskImg";
			}
		}
		else{
			if(this.cpDiskImgNameBuffer.get(stageIndex).size()>0){
				String diskImgName = this.cpDiskImgNameBuffer.get(stageIndex).pop();
				return diskImgName;
			}
			else{
				return "noAvailableDiskImg";
			}
		}
	}
	
	public void addDiskImgName(String chainName, int stageIndex, String diskImgName){
		if(chainName.equals("DATA")){
			this.dpDiskImgNameBuffer.get(stageIndex).push(diskImgName);
		}
		else{
			this.cpDiskImgNameBuffer.get(stageIndex).push(diskImgName);
		}
		if(!this.nameMap.containsKey(diskImgName)){
			this.nameMap.put(diskImgName, 0);
		}
	}
	
	public boolean containName(String diskImgName){
		if(this.nameMap.containsKey(diskImgName)){
			return true;
		}
		else{
			return false;
		}
	}
}
