package edu.sjsu.cmpe.cmpe283;

/*Import statements*/
import java.net.URL;

import com.vmware.vim25.GuestInfo;
import com.vmware.vim25.VirtualMachineCloneSpec;
import com.vmware.vim25.VirtualMachineConfigInfo;
import com.vmware.vim25.VirtualMachineMovePriority;
import com.vmware.vim25.VirtualMachinePowerState;
import com.vmware.vim25.VirtualMachineRelocateSpec;
import com.vmware.vim25.VirtualMachineRuntimeInfo;
import com.vmware.vim25.mo.Folder;
import com.vmware.vim25.mo.HostSystem;
import com.vmware.vim25.mo.InventoryNavigator;
import com.vmware.vim25.mo.ManagedEntity;
import com.vmware.vim25.mo.ResourcePool;
import com.vmware.vim25.mo.ServiceInstance;
import com.vmware.vim25.mo.Task;
import com.vmware.vim25.mo.VirtualMachine;
import com.vmware.vim25.mo.util.MorUtil;

/**
 * This class captures information of the host present in vCenter server,
 * along with the details of the different VMs running on the hosts. 
 * It also captures snapshot of the specified, along with cloning and 
 * migrating of the VM.
 * 
 * Author: Neha Viswanathan, 010029097
 * Date: 04-01-2015
 */
public class Hw2_097_Cmpe283_NavigatePaths {

	public static void main(String[] args) {
		ServiceInstance sInst = null;
		try {
			/*Setting command-line arguments */
			String ip = "";
			String login = "";
			String password = "";
			String vmFolderPath = "";

			if(args.length != 0 && args.length == 4) {
				ip = args[0];
				login = args[1];
				password = args[2];
				vmFolderPath = args[3];
			}
			else {
				System.err.println("Invalid number of arguments. Try again!");
				return;
			}
			sInst = new ServiceInstance(new URL(ip), login,password, true);
			Folder root = sInst.getRootFolder();

			if(vmFolderPath.equals("/")) {
				System.out.println("args[3] :: " + vmFolderPath);
				System.out.println("Datacenter folder :: " + root.getName());
				/*Retrieving host information*/
				ManagedEntity[] meHost = new InventoryNavigator(root).searchManagedEntities("HostSystem");
				if (meHost == null || meHost.length == 0) {
					return;
				}
				for(int hostCount=0; hostCount<meHost.length; hostCount++) {
					HostSystem host = (HostSystem) meHost[hostCount];
					System.out.println("Host[" + hostCount + "]");
					System.out.println("Host = " + host.getName());
					System.out.println("ProductFullName = " + sInst.getAboutInfo().getFullName());
				}

				int sCount = 0;
				/*Retrieving guest information*/
				int vmCount = 0;
				ManagedEntity[] meGuest = new InventoryNavigator(root).searchManagedEntities("VirtualMachine");
				if (meGuest == null) {
					System.err.println("Error occurred :: VM not found!");
					return;
				}

				/*Fetch information of every virtual machine*/
				for(int guestCount=0; guestCount<meGuest.length; guestCount++) { 
					VirtualMachine vm = (VirtualMachine) meGuest[guestCount];
					if(vm.getName().equalsIgnoreCase(vmFolderPath)) {
						VirtualMachineConfigInfo vminfo = vm.getConfig();
						System.out.println("VM[" + vmCount + "]");
						System.out.println("Name = " + vm.getName());
						System.out.println("GuestOS = " + vminfo.getGuestFullName());
						GuestInfo gi = vm.getGuest();
						System.out.println("Guest state = " + gi.getGuestState());
						VirtualMachineRuntimeInfo vmrinfo = vm.getRuntime();
						System.out.println("Power state = " + vmrinfo.getPowerState());
						/*Retrieve the host on which the VM is currently running*/
						ManagedEntity mEnt = MorUtil.createExactManagedEntity(sInst.getServerConnection(), vmrinfo.host);
						HostSystem hostSys = (HostSystem) mEnt;
						System.out.println("Host = " + hostSys.getName());

						/*Performing a snapshot*/
						String ssName = "snapshot"+sCount++;
						String ssDesc = "Snapshot of VM";
						Task task = vm.createSnapshot_Task(ssName, ssDesc, false, false);
						/*wait for snapshot task to complete*/
						task.waitForTask();
						/*print snapshot task status*/
						if(task.getTaskInfo().getState()!= null && 
								task.getTaskInfo().getState().toString().equalsIgnoreCase(task.SUCCESS)) {
							System.out.println("Snapshot creation status :: " + task.getTaskInfo().getState());
							System.out.println("Details for " + vm.getName() + " :: " + ssName + " ; " + ssDesc);
						}
						else {
							System.out.println("Error creating snapshot :: " + task.getTaskInfo().error.getLocalizedMessage());
						}

						/*Performing a clone of VM*/
						String cloneName = vm.getName()+"-clone-"+vmCount;
						/*Specifying configuration parameters for VM clone*/
						VirtualMachineCloneSpec vmCloneSpec = new VirtualMachineCloneSpec();
						vmCloneSpec.setLocation(new VirtualMachineRelocateSpec());
						vmCloneSpec.setPowerOn(false);
						vmCloneSpec.setTemplate(false);
						Task cloneTask = vm.cloneVM_Task((Folder)vm.getParent(), cloneName, vmCloneSpec);
						/*wait for clone task to complete*/
						cloneTask.waitForTask();
						/*print clone task status*/
						if(cloneTask.getTaskInfo().getState()!= null && 
								cloneTask.getTaskInfo().getState().toString().equalsIgnoreCase(cloneTask.SUCCESS)) {
							System.out.println("VM Clone creation status :: " + cloneTask.getTaskInfo().getState());
							System.out.println("Details for " + vm.getName() + " Clone :: " + cloneName);
						}
						else {
							System.out.println("Error while cloning VM :: " + cloneTask.getTaskInfo().error.getLocalizedMessage());
						} 

						/*Migrating VM between hosts only if number of hosts is greater than 1*/
						if(meHost.length > 1) {
							/*if the desired VM is present, only then continue with migration to a different host*/
							if(vm.getName().equalsIgnoreCase(vmFolderPath)) {
								/*iterate through all the VMs present*/
								for(int hCount = 0; hCount < meHost.length; hCount++) {
									/*check if the host does not contain the VM to be migrated*/
									if(!hostSys.getName().equals(meHost[hCount].getName())) {
										/*If VM is powered ON, retain the same state while migration*/
										if(vmrinfo.getPowerState().toString().equalsIgnoreCase("poweredOn")) {
											ManagedEntity mEntity = new InventoryNavigator(root).searchManagedEntity("HostSystem", meHost[hCount].getName());
											ResourcePool rPool = vm.getResourcePool();
											HostSystem destHost = (HostSystem) mEntity;
											Task migrateTask = vm.migrateVM_Task(rPool, destHost, VirtualMachineMovePriority.highPriority, VirtualMachinePowerState.poweredOn);
											migrateTask.waitForTask();
											if(migrateTask.getTaskInfo().getState()!= null && 
													migrateTask.getTaskInfo().getState().toString().equalsIgnoreCase(migrateTask.SUCCESS)) {
												System.out.println("VM migration status :: " + migrateTask.getTaskInfo().getState());
												System.out.println("Details for " + vm.getName() + " migrated to host :: " + destHost.toString());
											}
											else {
												System.out.println("Error while migrating VM :: " + migrateTask.getTaskInfo().error.getLocalizedMessage());
											} 
										}
										/*If VM is powered OFF, retain the same state while migration*/
										else {
											ManagedEntity mEntity = new InventoryNavigator(root).searchManagedEntity("HostSystem", meHost[hCount].getName());
											ResourcePool rPool = vm.getResourcePool();
											HostSystem destHost = (HostSystem) hostSys;
											Task migrateTask = vm.migrateVM_Task(rPool, destHost, VirtualMachineMovePriority.highPriority, VirtualMachinePowerState.poweredOff);
											migrateTask.waitForTask();
											if(migrateTask.getTaskInfo().getState()!= null &&
													migrateTask.getTaskInfo().getState().toString().equalsIgnoreCase(migrateTask.SUCCESS)) {
												System.out.println("VM migration status :: " + migrateTask.getTaskInfo().getState());
												System.out.println("Details for " + vm.getName() + " migrated to host :: " + destHost.toString());
											}
											else {
												System.out.println("Error while migrating VM :: " + migrateTask.getTaskInfo().error.getLocalizedMessage());
											} 
										}
									}
								}
							}
						}
						/*If only one host exists, display an error message*/
						else {
							System.out.println("Migration skipped: only one host exists! :( ");
						}

						/*Get a list of recent tasks on each VM with start time, completed time and status*/
						Task[] taskArr = meGuest[guestCount].getRecentTasks();
						for(Task tsk : taskArr) {
							if(tsk.getTaskInfo().getState().toString().equalsIgnoreCase(tsk.SUCCESS)) {
								System.out.println("task: target = " + vm.getName() + 
										" op = " + tsk.getTaskInfo().getName() + 
										" startTime = " + tsk.getTaskInfo().getStartTime().getTime() + 
										" endTime = " + tsk.getTaskInfo().getCompleteTime().getTime() + 
										" status = " + tsk.getTaskInfo().getState().toString());
							}
							/*Print localized message in case of an error*/
							else {
								System.out.println("task: target = " + vm.getName() + 
										" op = " + tsk.getTaskInfo().getName() + 
										" startTime = " + tsk.getTaskInfo().getStartTime().getTime() + 
										" endTime = " + tsk.getTaskInfo().getCompleteTime().getTime() + 
										" status = " + tsk.getTaskInfo().error.getLocalizedMessage());
							}	
						}
					}
					/*increment VM count*/
					vmCount++;
				}
			}
			else {
				System.out.println("args[3] :: " + vmFolderPath);
				System.out.println("Datacenter folder :: " + root.getName());

				/*Retrieving host information*/
				ManagedEntity[] meHost = new InventoryNavigator(root).searchManagedEntities("HostSystem");
				if (meHost == null || meHost.length == 0) {
					return;
				}
				for(int hostCount=0; hostCount<meHost.length; hostCount++) {
					HostSystem host = (HostSystem) meHost[hostCount];
					System.out.println("Host[" + hostCount + "]");
					System.out.println("Host = " + host.getName());
					System.out.println("ProductFullName = " + sInst.getAboutInfo().getFullName());
				}

				String newFolder = vmFolderPath.substring(0,10);
				String end = vmFolderPath.substring(10);
				String newVMFolderPath =  newFolder+"vm/"+end;
				System.out.println("Inventory Path is :: " + newVMFolderPath);
				
				ManagedEntity manEntity = sInst.getSearchIndex().findByInventoryPath((newVMFolderPath));
				System.out.println("VM Folder :: " + manEntity.getName());
				
				/*Retrieving guest information*/
				int sCount = 0;
				int vmCount = 0;
				ManagedEntity[] childEnt = ((Folder)manEntity).getChildEntity();
				/*Fetch information of every virtual machine*/
				for(int guestCount=0; guestCount<childEnt.length; guestCount++) { 
					VirtualMachine vm = (VirtualMachine) childEnt[guestCount];
					VirtualMachineConfigInfo vminfo = vm.getConfig();
					System.out.println("VM[" + vmCount + "]");
					System.out.println("Name = " + vm.getName());
					System.out.println("GuestOS = " + vminfo.getGuestFullName());
					GuestInfo gi = vm.getGuest();
					System.out.println("Guest state = " + gi.getGuestState());
					VirtualMachineRuntimeInfo vmrinfo = vm.getRuntime();
					System.out.println("Power state = " + vmrinfo.getPowerState());
					/*Retrieve the host on which the VM is currently running*/
					ManagedEntity mEnt = MorUtil.createExactManagedEntity(sInst.getServerConnection(), vmrinfo.host);
					HostSystem hostSys = (HostSystem) mEnt;
					System.out.println("Host = " + hostSys.getName());

					/*Performing a snapshot*/
					String ssName = "snapshot"+sCount++;
					String ssDesc = "Snapshot of VM";
					Task task = vm.createSnapshot_Task(ssName, ssDesc, false, false);
					/*wait for snapshot task to complete*/
					task.waitForTask();
					/*print snapshot task status*/
					if(task.getTaskInfo().getState()!= null && 
							task.getTaskInfo().getState().toString().equalsIgnoreCase(task.SUCCESS)) {
						System.out.println("Snapshot creation status :: " + task.getTaskInfo().getState());
						System.out.println("Details for " + vm.getName() + " :: " + ssName + " ; " + ssDesc);
					}
					else {
						System.out.println("Error creating snapshot :: " + task.getTaskInfo().error.getLocalizedMessage());
					}

					/*Performing a clone of VM*/
					String cloneName = vm.getName()+"-clone-"+vmCount;
					/*Specifying configuration parameters for VM clone*/
					VirtualMachineCloneSpec vmCloneSpec = new VirtualMachineCloneSpec();
					vmCloneSpec.setLocation(new VirtualMachineRelocateSpec());
					vmCloneSpec.setPowerOn(false);
					vmCloneSpec.setTemplate(false);
					Task cloneTask = vm.cloneVM_Task((Folder)vm.getParent(), cloneName, vmCloneSpec);
					/*wait for clone task to complete*/
					cloneTask.waitForTask();
					/*print clone task status*/
					if(cloneTask.getTaskInfo().getState()!= null && 
							cloneTask.getTaskInfo().getState().toString().equalsIgnoreCase(cloneTask.SUCCESS)) {
						System.out.println("VM Clone creation status :: " + cloneTask.getTaskInfo().getState());
						System.out.println("Details for " + vm.getName() + " Clone :: " + cloneName);
					}
					else {
						System.out.println("Error while cloning VM :: " + cloneTask.getTaskInfo().error.getLocalizedMessage());
					} 

					/*Migrating VM between hosts only if number of hosts is greater than 1*/
					if(meHost.length > 1) {
						/*if the desired VM is present, only then continue with migration to a different host*/
						if(vm.getName().equalsIgnoreCase(vmFolderPath)) {
							/*iterate through all the VMs present*/
							for(int hCount = 0; hCount < meHost.length; hCount++) {
								/*check if the host does not contain the VM to be migrated*/
								if(!hostSys.getName().equals(meHost[hCount].getName())) {
									/*If VM is powered ON, retain the same state while migration*/
									if(vmrinfo.getPowerState().toString().equalsIgnoreCase("poweredOn")) {
										ManagedEntity mEntity = new InventoryNavigator(root).searchManagedEntity("HostSystem", meHost[hCount].getName());
										ResourcePool rPool = vm.getResourcePool();
										HostSystem destHost = (HostSystem) mEntity;
										Task migrateTask = vm.migrateVM_Task(rPool, destHost, VirtualMachineMovePriority.highPriority, VirtualMachinePowerState.poweredOn);
										migrateTask.waitForTask();
										if(migrateTask.getTaskInfo().getState()!= null && 
												migrateTask.getTaskInfo().getState().toString().equalsIgnoreCase(migrateTask.SUCCESS)) {
											System.out.println("VM migration status :: " + migrateTask.getTaskInfo().getState());
											System.out.println("Details for " + vm.getName() + " migrated to host :: " + destHost.toString());
										}
										else {
											System.out.println("Error while migrating VM :: " + migrateTask.getTaskInfo().error.getLocalizedMessage());
										} 
									}
									/*If VM is powered OFF, retain the same state while migration*/
									else {
										ManagedEntity mEntity = new InventoryNavigator(root).searchManagedEntity("HostSystem", meHost[hCount].getName());
										ResourcePool rPool = vm.getResourcePool();
										HostSystem destHost = (HostSystem) hostSys;
										Task migrateTask = vm.migrateVM_Task(rPool, destHost, VirtualMachineMovePriority.highPriority, VirtualMachinePowerState.poweredOff);
										migrateTask.waitForTask();
										if(migrateTask.getTaskInfo().getState()!= null &&
												migrateTask.getTaskInfo().getState().toString().equalsIgnoreCase(migrateTask.SUCCESS)) {
											System.out.println("VM migration status :: " + migrateTask.getTaskInfo().getState());
											System.out.println("Details for " + vm.getName() + " migrated to host :: " + destHost.toString());
										}
										else {
											System.out.println("Error while migrating VM :: " + migrateTask.getTaskInfo().error.getLocalizedMessage());
										} 
									}
								}
							}
						}
					}
					/*If only one host exists, display an error message*/
					else {
						System.out.println("Migration skipped: only one host exists! :( ");
					}

					/*Get a list of recent tasks on each VM with start time, completed time and status*/
					Task[] taskArr = childEnt[guestCount].getRecentTasks();
					for(Task tsk : taskArr) {
						if(tsk.getTaskInfo().getState().toString().equalsIgnoreCase(tsk.SUCCESS)) {
							System.out.println("task: target = " + vm.getName() + 
									" op = " + tsk.getTaskInfo().getName() + 
									" startTime = " + tsk.getTaskInfo().getStartTime().getTime() + 
									" endTime = " + tsk.getTaskInfo().getCompleteTime().getTime() + 
									" status = " + tsk.getTaskInfo().getState().toString());
						}
						/*Print localized message in case of an error*/
						else {
							System.out.println("task: target = " + vm.getName() + 
									" op = " + tsk.getTaskInfo().getName() + 
									" startTime = " + tsk.getTaskInfo().getStartTime().getTime() + 
									" endTime = " + tsk.getTaskInfo().getCompleteTime().getTime() + 
									" status = " + tsk.getTaskInfo().error.getLocalizedMessage());
						}	
					}
					/*increment VM count*/
					vmCount++;
				}
			}
		}
		/*Catch exception if any*/
		catch(Exception e) {
			System.err.println("Exception occurred :: " + e.getMessage());
		}

		/*Logout of connection*/
		finally {
			if(sInst != null) {
				System.out.println("Logging out!");
				sInst.getServerConnection().logout();
			}
		}
	}


}


