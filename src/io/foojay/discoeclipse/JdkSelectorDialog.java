package io.foojay.discoeclipse;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.URL;
import java.net.URLConnection;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.util.Comparator;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Font;
import org.eclipse.swt.graphics.FontData;
import org.eclipse.swt.graphics.Point;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.DirectoryDialog;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.ProgressBar;
import org.eclipse.swt.widgets.Shell;

import io.foojay.api.discoclient.DiscoClient;
import io.foojay.api.discoclient.pkg.Architecture;
import io.foojay.api.discoclient.pkg.ArchiveType;
import io.foojay.api.discoclient.pkg.Distribution;
import io.foojay.api.discoclient.pkg.LibCType;
import io.foojay.api.discoclient.pkg.MajorVersion;
import io.foojay.api.discoclient.pkg.Match;
import io.foojay.api.discoclient.pkg.OperatingSystem;
import io.foojay.api.discoclient.pkg.PackageType;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.pkg.ReleaseStatus;
import io.foojay.api.discoclient.pkg.Scope;
import io.foojay.api.discoclient.pkg.SemVer;
import io.foojay.api.discoclient.pkg.VersionNumber;
import io.foojay.api.discoclient.util.OutputFormat;
import io.foojay.api.discoclient.util.PkgInfo;
import io.foojay.api.discoclient.util.ReadableConsumerByteChannel;


public class JdkSelectorDialog extends Dialog {
	private Display              display;
	private DiscoClient 	     discoClient;
	private OperatingSystem      operatingSystem;
	private List<MajorVersion>   maintainedVersions;
	private List<Pkg>            selectedPkgs;
	private Pkg                  selectedPkg;
	private List<Pkg>            selectedPkgsForMajorVersion;
	
	private List<Distribution>   distributionsThatSupportFx;
	private Button 				 javafxBundledCheckBox;
	private Combo  			     majorVersionComboBox;
	private Combo  			     versionNumberComboBox;
	private Combo  			     distributionComboBox;
	private Combo  			     operatingSystemComboBox;
	private Combo			     libcTypeComboBox;
	private Combo  			     architectureComboBox;
	private Combo  			     archiveTypeComboBox;
	private Label  			     filenameLabel;
	private ProgressBar 	     progressBar;
	private Button      	     downloadButton;
	
	private boolean              javafxBundled;
    private MajorVersion         selectedMajorVersion;
    private SemVer               selectedVersionNumber;
    private Distribution         selectedDistribution;
    private OperatingSystem      selectedOperatingSystem;
    private LibCType             selectedLibcType;
    private Architecture         selectedArchitecture;
    private ArchiveType          selectedArchiveType;
    private Set<OperatingSystem> operatingSystems;
    private Set<Architecture>    architectures;
    private Set<LibCType>        libcTypes;
    private Set<ArchiveType>     archiveTypes;
		
	
	public JdkSelectorDialog(final Shell parentShell) {
		super(parentShell);
		display            			= parentShell.getDisplay();
		discoClient        			= new DiscoClient("Eclipse");
		operatingSystem    			= DiscoClient.getOperatingSystem();
		maintainedVersions 		    = new LinkedList<>();
		selectedPkgs       			= new LinkedList<>();
		selectedPkg        			= null;
		selectedPkgsForMajorVersion = new LinkedList<>();
		javafxBundled      			= false;
        operatingSystems   			= new TreeSet<>();
        libcTypes          			= new TreeSet<>();
        architectures      			= new TreeSet<>();
        archiveTypes                = new TreeSet<>();
        distributionsThatSupportFx  = List.of(DiscoClient.getDistributionFromText("zulu"), DiscoClient.getDistributionFromText("liberica"), DiscoClient.getDistributionFromText("corretto"));
	}
	
	
	@Override public void create() {
		super.create();		
		
		if (null == display) { return; }
		
		display.asyncExec(() -> {
			maintainedVersions.addAll(discoClient.getMaintainedMajorVersions(true,  true));	
            maintainedVersions.forEach(majorVersion -> majorVersionComboBox.add(Integer.toString(majorVersion.getAsInt())));
            majorVersionComboBox.select(0);
            majorVersionComboBox.notifyListeners(SWT.Selection, new Event());
		});
	}
	
	@Override protected Control createDialogArea(Composite parent) {
		Composite  container = (Composite) super.createDialogArea(parent);
        GridLayout layout    = new GridLayout(2, false);
        container.setLayout(layout);
        
        javafxBundledCheckBox = new Button(container, SWT.CHECK);
        javafxBundledCheckBox.setText("JavaFX bundled");
        javafxBundledCheckBox.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
        javafxBundledCheckBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				javafxBundled = javafxBundledCheckBox.getSelection();
				
				boolean include_build = selectedMajorVersion.isEarlyAccessOnly();
	            List<Distribution> distrosForSelection = selectedPkgsForMajorVersion.stream()
	                                                                                .filter(pkg -> javafxBundled == pkg.isJavaFXBundled())
	                                                                                .filter(pkg -> pkg.getJavaVersion().getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED,true,include_build).equals(selectedVersionNumber.getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED,true,include_build)))
	                                                                                .map(pkg -> pkg.getDistribution())
	                                                                                .distinct()
	                                                                                .sorted(Comparator.comparing(Distribution::getName).reversed())
	                                                                                .collect(Collectors.toList());
	            display.asyncExec(() -> {
	    			distributionComboBox.removeAll();
	    			
	    			distrosForSelection.stream().forEach(distro -> distributionComboBox.add(distro.getUiString()));
	    			if (distrosForSelection.size() > 0) {
	    		        distributionComboBox.select(0);		
	    		        distributionComboBox.notifyListeners(SWT.Selection, new Event());
	    			} else {
	    				operatingSystemComboBox.removeAll();
	    				libcTypeComboBox.removeAll();
	    				architectureComboBox.removeAll();
	    				archiveTypeComboBox.removeAll();
	    				filenameLabel.setText("-");
	    			}
	    		    selectDistribution();
	    		});
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        Label majorVersionLabel = new Label(container, SWT.BORDER);
        majorVersionLabel.setText("Major version:");
        majorVersionLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        majorVersionComboBox = new Combo(container, SWT.NONE);
        final GridData majorVersionData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        majorVersionData.widthHint = 150;
        majorVersionComboBox.setLayoutData(majorVersionData);
        majorVersionComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) { 
				if (-1 == majorVersionComboBox.getSelectionIndex()) { return; }
				selectMajorVersion(); 
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        
        Label versionNumberLabel = new Label(container, SWT.BORDER);
        versionNumberLabel.setText("Version number:");
        versionNumberLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        versionNumberComboBox = new Combo(container, SWT.NONE);
        final GridData versionNumberData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        versionNumberData.widthHint = 150;
        versionNumberComboBox.setLayoutData(versionNumberData);
        versionNumberComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) { 
				if (-1 == majorVersionComboBox.getSelectionIndex()) { return; }
				if (-1 == versionNumberComboBox.getSelectionIndex()) { return; }
				selectVersionNumber(); 
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        Label distributionLabel = new Label(container, SWT.BORDER);
        distributionLabel.setText("Distribution:");
        distributionLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        distributionComboBox = new Combo(container, SWT.NONE);
        final GridData distributionData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        distributionData.widthHint = 150;
        distributionComboBox.setLayoutData(distributionData);
        distributionComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				if (-1 == majorVersionComboBox.getSelectionIndex()) { return; }
				if (-1 == versionNumberComboBox.getSelectionIndex()) { return; }
				if (-1 == operatingSystemComboBox.getSelectionIndex()) { return; }
				selectDistribution();
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        Label operatingSystemLabel = new Label(container, SWT.BORDER);
        operatingSystemLabel.setText("Operating system:");
        operatingSystemLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        operatingSystemComboBox = new Combo(container, SWT.NONE);
        final GridData operatingSystemData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        operatingSystemData.widthHint = 150;
        operatingSystemComboBox.setLayoutData(operatingSystemData);
        operatingSystemComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				if (-1 == majorVersionComboBox.getSelectionIndex()) { return; }
				if (-1 == versionNumberComboBox.getSelectionIndex()) { return; }
				if (-1 == operatingSystemComboBox.getSelectionIndex()) { return; }
				if (-1 == distributionComboBox.getSelectionIndex()) { return ; }
				selectOperatingSystem();
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        Label libcTypeLabel = new Label(container, SWT.BORDER);
        libcTypeLabel.setText("Libc type:");
        libcTypeLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        libcTypeComboBox = new Combo(container, SWT.NONE);
        final GridData libcTypeData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        libcTypeData.widthHint = 150;
        libcTypeComboBox.setLayoutData(libcTypeData);
        libcTypeComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				if (-1 == majorVersionComboBox.getSelectionIndex()) { return; }
				if (-1 == versionNumberComboBox.getSelectionIndex()) { return; }
				if (-1 == operatingSystemComboBox.getSelectionIndex()) { return; }
				if (-1 == distributionComboBox.getSelectionIndex()) { return ; }
				selectLibcType();
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        Label architectureLabel = new Label(container, SWT.BORDER);
        architectureLabel.setText("Architecture:");
        architectureLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        architectureComboBox = new Combo(container, SWT.NONE);
        final GridData architectureData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        architectureData.widthHint = 150;
        architectureComboBox.setLayoutData(architectureData);
        architectureComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				if (-1 == majorVersionComboBox.getSelectionIndex()) { return; }
				if (-1 == versionNumberComboBox.getSelectionIndex()) { return; }
				if (-1 == operatingSystemComboBox.getSelectionIndex()) { return; }
				if (-1 == distributionComboBox.getSelectionIndex()) { return ; }
				selectArchitecture();	
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        Label archiveTypeLabel = new Label(container, SWT.BORDER);
        archiveTypeLabel.setText("Archive type:");
        archiveTypeLabel.setLayoutData(new GridData(SWT.BEGINNING, SWT.CENTER, false, false));
        
        archiveTypeComboBox = new Combo(container, SWT.NONE);
        final GridData archiveTypeData = new GridData(SWT.BEGINNING, SWT.CENTER, true, false);
        archiveTypeData.widthHint = 150;
        archiveTypeComboBox.setLayoutData(archiveTypeData);
        archiveTypeComboBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				selectArchiveType();
			}

			@Override public void widgetDefaultSelected(SelectionEvent e) {}
        });
        
        
        filenameLabel = new Label(container, SWT.BORDER);
        filenameLabel.setText("-");
        final GridData filenameLabelData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        filenameLabelData.widthHint = 250;
        filenameLabel.setLayoutData(filenameLabelData);
        FontData[] fontData = filenameLabel.getFont().getFontData();
        fontData[0].setHeight(10);
        filenameLabel.setFont(new Font(display, fontData[0]));
        
        
        progressBar = new ProgressBar(container, SWT.HORIZONTAL);
        progressBar.setMinimum(0);
        progressBar.setMaximum(100);
        final GridData progressBarData = new GridData(SWT.CENTER, SWT.CENTER, true, false, 2, 1);
        progressBarData.widthHint = 250;
        progressBar.setLayoutData(progressBarData);
        
        
        downloadButton = new Button(container, SWT.PUSH);
        downloadButton.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, false, false, 2, 1));
        downloadButton.setText("Download");
        downloadButton.setToolTipText("Choose destination folder and start download");
        downloadButton.addSelectionListener(new SelectionAdapter() {
            @Override public void widgetSelected(SelectionEvent e) {
                downloadPkg();
            }
        });
        
        
        javafxBundledCheckBox.pack();
        majorVersionLabel.pack();
        majorVersionComboBox.pack();
        versionNumberLabel.pack();
        versionNumberComboBox.pack();
        distributionLabel.pack();
        distributionComboBox.pack();
        operatingSystemLabel.pack();
        operatingSystemComboBox.pack();
        architectureLabel.pack();
        architectureComboBox.pack();
        archiveTypeLabel.pack();
        archiveTypeComboBox.pack();
        progressBar.pack();
        filenameLabel.pack();
        downloadButton.pack();
                
		
        return container;
    }

    @Override protected void configureShell(Shell newShell) {
        super.configureShell(newShell);
        newShell.setText("Foojay JDK Discovery Service");
    }

    @Override protected Point getInitialSize() {
        return new Point(280, 400);
    }
    
    
    private void selectMajorVersion() {
    	String[] items         = majorVersionComboBox.getItems();
		int      selectedIndex = majorVersionComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }				
		selectedMajorVersion = maintainedVersions.get(selectedIndex);
    	final boolean include_build = selectedMajorVersion.isEarlyAccessOnly();
    	display.asyncExec(() -> javafxBundledCheckBox.setEnabled(false));
    	discoClient.getPkgsForFeatureVersionAsync(distributionsThatSupportFx, selectedMajorVersion.getAsInt(), include_build ? List.of(ReleaseStatus.EA) : List.of(ReleaseStatus.GA), true, List.of(Scope.PUBLIC, Scope.DIRECTLY_DOWNLOADABLE, Scope.BUILD_OF_OPEN_JDK), Match.ANY)
        		   .thenAccept(pkgs -> {
			            selectedPkgsForMajorVersion.clear();
			            selectedPkgsForMajorVersion.addAll(pkgs);
			            display.asyncExec(() -> {
			            	javafxBundledCheckBox.setEnabled(true);
			                versionNumberComboBox.notifyListeners(SWT.Selection, new Event());
			            });
			            selectVersionNumber();
			        });
    	
    	
    	List<SemVer> versionList = selectedMajorVersion.getVersions()
										               .stream()
										               .filter(semVer -> selectedMajorVersion.isEarlyAccessOnly() ? (semVer.getReleaseStatus() == ReleaseStatus.EA) : (semVer.getReleaseStatus() == ReleaseStatus.GA))
										               .sorted(Comparator.comparing(SemVer::getVersionNumber).reversed())
										               .map(semVer -> semVer.getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED, true, include_build))
										               .distinct().map(versionString -> SemVer.fromText(versionString).getSemVer1())
										               .collect(Collectors.toList());
    	
		display.asyncExec(() -> {
		    versionNumberComboBox.removeAll();
		    versionList.forEach(version -> versionNumberComboBox.add(version.toString(true)));
		    versionNumberComboBox.select(0);
		    versionNumberComboBox.notifyListeners(SWT.Selection, new Event());
		    selectVersionNumber();
		});
    }
    
    private void selectVersionNumber() {
    	String[] items         = versionNumberComboBox.getItems();
		int      selectedIndex = versionNumberComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }
		
		display.asyncExec(() -> {
			selectedVersionNumber = SemVer.fromText(items[selectedIndex]).getSemVer1();
			
			List<Distribution> distrosForSelection;
			if (javafxBundled) {
				boolean include_build = selectedMajorVersion.isEarlyAccessOnly();
				distrosForSelection = selectedPkgsForMajorVersion.stream()
                        .filter(pkg -> javafxBundled == pkg.isJavaFXBundled())
                        .filter(pkg -> pkg.getJavaVersion().getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED,true,include_build).equals(selectedVersionNumber.getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED,true,include_build)))
                        .map(pkg -> pkg.getDistribution())
                        .distinct()
                        .sorted(Comparator.comparing(Distribution::getName).reversed())
                        .collect(Collectors.toList());
			} else {
				distrosForSelection = discoClient.getDistributionsForSemVerAsync(selectedVersionNumber)
                        .thenApply(distros -> distros.stream()
                                                     .filter(distro -> distro.getScopes().contains(Scope.DIRECTLY_DOWNLOADABLE))
                                                     .sorted(Comparator.comparing(Distribution::getName).reversed()))
                        .join()
                        .collect(Collectors.toList());
			}
			
			distributionComboBox.removeAll();
			distrosForSelection.stream().forEach(distro -> distributionComboBox.add(distro.getUiString()));
			if (distrosForSelection.size() > 0) {
				distributionComboBox.select(0);		
			    distributionComboBox.notifyListeners(SWT.Selection, new Event());
			} else {
				operatingSystemComboBox.removeAll();
                libcTypeComboBox.removeAll();
                architectureComboBox.removeAll();
                archiveTypeComboBox.removeAll();
			}
		    selectDistribution();
		});
    }
    
    private void selectDistribution() {
    	String[] items         = distributionComboBox.getItems();
		int      selectedIndex = distributionComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }
		
		selectedDistribution = discoClient.getDistributionFromText(items[selectedIndex]);
		
		selectedPkgs.clear();
        operatingSystems.clear();
        architectures.clear();
        libcTypes.clear();
        archiveTypes.clear();
		
        selectedPkgs.addAll(discoClient.getPkgs(List.of(selectedDistribution), VersionNumber.fromText((selectedVersionNumber).toString(true)), null, null, null, null, null, null, PackageType.JDK,
                								null, true, null, null,List.of(Scope.PUBLIC, Scope.DIRECTLY_DOWNLOADABLE, Scope.BUILD_OF_OPEN_JDK), Match.ANY));
        selectedPkgs.forEach(pkg -> {
            operatingSystems.add(pkg.getOperatingSystem());
            architectures.add(pkg.getArchitecture());
            libcTypes.add(pkg.getLibCType());
            archiveTypes.add(pkg.getArchiveType());
        });
        
        
		display.asyncExec(() -> {
			operatingSystemComboBox.removeAll();
			operatingSystems.forEach(os -> operatingSystemComboBox.add(os.getUiString()));
			for (int i = 0 ; i < operatingSystems.size() ; i++) {
				if (operatingSystemComboBox.getItem(i).equals(discoClient.getOperatingSystem().getUiString())) {
					operatingSystemComboBox.select(i);
					break;
				}
			}
			operatingSystemComboBox.notifyListeners(SWT.Selection, new Event());
			selectOperatingSystem();
		});
    }
    
    private void selectOperatingSystem() {
    	String[] items         = operatingSystemComboBox.getItems();
		int      selectedIndex = operatingSystemComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }
		
		selectedOperatingSystem = OperatingSystem.fromText(items[selectedIndex]);
		List<Pkg> selection = selectedPkgs.stream()
                .filter(pkg -> pkg.isJavaFXBundled() == javafxBundled)
                .filter(pkg -> selectedDistribution.getApiString().equals(pkg.getDistribution().getApiString()))
                .filter(pkg -> selectedOperatingSystem == pkg.getOperatingSystem())
                .collect(Collectors.toList());
        libcTypes = selection.stream().map(pkg -> pkg.getLibCType()).collect(Collectors.toSet());
		display.asyncExec(() -> {
            libcTypeComboBox.removeAll();
            libcTypes.forEach(libcType -> libcTypeComboBox.add(libcType.getUiString()));
            libcTypeComboBox.select(0);
            libcTypeComboBox.notifyListeners(SWT.Selection, new Event());
            selectLibcType();
		});
    }
    
    private void selectLibcType() {
    	String[] items         = libcTypeComboBox.getItems();
		int      selectedIndex = libcTypeComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }
				
		selectedLibcType = LibCType.fromText(items[selectedIndex]);
        
		List<Pkg> selection = selectedPkgs.stream()
                                          .filter(pkg -> pkg.isJavaFXBundled() == javafxBundled)
                                          .filter(pkg -> selectedDistribution.getApiString().equals(pkg.getDistribution().getApiString()))
                                          .filter(pkg -> selectedOperatingSystem == pkg.getOperatingSystem())
                                          .filter(pkg -> selectedLibcType        == pkg.getLibCType())
                                          .collect(Collectors.toList());
        architectures = selection.stream().map(pkg -> pkg.getArchitecture()).collect(Collectors.toSet());
		display.asyncExec(() -> {			
            architectureComboBox.removeAll();
            architectures.forEach(architecture -> architectureComboBox.add(architecture.getUiString()));
            architectureComboBox.select(0);
            architectureComboBox.notifyListeners(SWT.Selection, new Event());
            selectArchitecture();
		});
    }
    
    private void selectArchitecture() {
    	String[] items         = architectureComboBox.getItems();
		int      selectedIndex = architectureComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }
		
		selectedArchitecture = Architecture.fromText(items[selectedIndex]);
		List<Pkg> selection = selectedPkgs.stream()
                .filter(pkg -> pkg.isJavaFXBundled() == javafxBundled)
                .filter(pkg -> selectedDistribution.getApiString().equals(pkg.getDistribution().getApiString()))
                .filter(pkg -> selectedOperatingSystem == pkg.getOperatingSystem())
                .filter(pkg -> selectedLibcType        == pkg.getLibCType())
                .filter(pkg -> selectedArchitecture    == pkg.getArchitecture())
                .collect(Collectors.toList());
        archiveTypes = selection.stream().map(pkg -> pkg.getArchiveType()).collect(Collectors.toSet());
		display.asyncExec(() -> {
            archiveTypeComboBox.removeAll();
            archiveTypes.forEach(archiveType -> archiveTypeComboBox.add(archiveType.getUiString()));
            archiveTypeComboBox.select(0);
            archiveTypeComboBox.notifyListeners(SWT.Selection, new Event());
            selectArchiveType();
		});
    }
    
    private void selectArchiveType() {
    	String[] items         = archiveTypeComboBox.getItems();
		int      selectedIndex = archiveTypeComboBox.getSelectionIndex();
		if (items.length == 0 || selectedIndex == -1) { return; }
		
		selectedArchiveType = ArchiveType.fromText(items[selectedIndex]);
		update();
    }
    
    private void update() {
    	List<Pkg> selection = selectedPkgs.stream()
                .filter(pkg -> pkg.isJavaFXBundled()   == javafxBundled)
                .filter(pkg -> selectedDistribution.getApiString().equals(pkg.getDistribution().getApiString()))
                .filter(pkg -> selectedOperatingSystem == pkg.getOperatingSystem())
                .filter(pkg -> selectedLibcType        == pkg.getLibCType())
                .filter(pkg -> selectedArchitecture    == pkg.getArchitecture())
                .filter(pkg -> selectedArchiveType     == pkg.getArchiveType())
                .collect(Collectors.toList());
    	
    	if (selection.size() > 0) {
            selectedPkg = selection.get(0);
            display.asyncExec(() -> filenameLabel.setText(null == selectedPkg ? "-" : selectedPkg.getFileName()));
        } else {
            selectedPkg = null;
            display.asyncExec(() -> filenameLabel.setText("-"));
        }
    }
    
    private void downloadPkg() {
        if (null == selectedPkg) { return; }
                
        Shell shell = display.getActiveShell();
        DirectoryDialog dialog = new DirectoryDialog(shell);
        dialog.setMessage("Select destination folder");
        dialog.setText("Select destination folder");
        dialog.setFilterPath(System.getProperty("user.home"));
        String targetFolder = dialog.open();
        
        if (null == targetFolder) { return; }
               
        PkgInfo pkgInfo  = discoClient.getPkgInfo(selectedPkg.getEphemeralId(), selectedPkg.getJavaVersion());
        String  filename = pkgInfo.getFileName();
        
        downloadButton.setEnabled(false);
        String targetFilename = targetFolder + File.separator + filename;
        String directDownloadUri = pkgInfo.getDirectDownloadUri();
        
        Runnable downloadTask = () -> {
        	try {
                final URLConnection               connection = new URL(directDownloadUri).openConnection();
                final int                         fileSize   = connection.getContentLength();
                final ReadableByteChannel         rbc        = Channels.newChannel(connection.getInputStream());
                final ReadableConsumerByteChannel rcbc       = new ReadableConsumerByteChannel(rbc, (b) -> {
                	display.asyncExec(() -> progressBar.setSelection((int) ((double) b / (double) fileSize * 100)));
                });                
                final FileOutputStream            fos        = new FileOutputStream(targetFilename);
                fos.getChannel().transferFrom(rcbc, 0, Long.MAX_VALUE);
                fos.close();
                rcbc.close();
                rbc.close();
            } catch (IOException ex) {
            
            } finally {
        	    display.asyncExec(() -> {
        	    	progressBar.setSelection(0);
        	    	downloadButton.setEnabled(true);
        	    });
            }
        };
        new Thread(downloadTask).start();
    }
}
