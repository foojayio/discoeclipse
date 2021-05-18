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
import io.foojay.api.discoclient.pkg.OperatingSystem;
import io.foojay.api.discoclient.pkg.PackageType;
import io.foojay.api.discoclient.pkg.Pkg;
import io.foojay.api.discoclient.pkg.Scope;
import io.foojay.api.discoclient.pkg.SemVer;
import io.foojay.api.discoclient.pkg.VersionNumber;
import io.foojay.api.discoclient.util.OutputFormat;
import io.foojay.api.discoclient.util.PkgInfo;
import io.foojay.api.discoclient.util.ReadableConsumerByteChannel;


public class JdkSelectorDialog extends Dialog {
	private Display            display;
	private DiscoClient 	   discoClient;
	private OperatingSystem    operatingSystem;
	private List<MajorVersion> maintainedVersions;
	private List<Pkg>          selectedPkgs;
	private Pkg                selectedPkg;
	private Combo  			   majorVersionComboBox;
	private Combo  			   versionNumberComboBox;
	private Combo  			   distributionComboBox;
	private Combo  			   operatingSystemComboBox;
	private Combo			   libcTypeComboBox;
	private Combo  			   architectureComboBox;
	private Combo  			   archiveTypeComboBox;
	private Label  			   filenameLabel;
	private ProgressBar 	   progressBar;
	private Button      	   downloadButton;
		
	
	public JdkSelectorDialog(final Shell parentShell) {
		super(parentShell);
		display            = parentShell.getDisplay();
		discoClient        = new DiscoClient("Eclipse");
		operatingSystem    = DiscoClient.getOperatingSystem();
		maintainedVersions = new LinkedList<>();
		selectedPkgs       = new LinkedList<>();
		selectedPkg        = null;
	}
	
	
	@Override public void create() {
		super.create();		
		
		if (null == display) { return; }
		
		display.asyncExec(() -> {
			maintainedVersions.addAll(discoClient.getMaintainedMajorVersions(true,  true));	
            maintainedVersions.forEach(majorVersion -> majorVersionComboBox.add(Integer.toString(majorVersion.getAsInt())));
            operatingSystemComboBox.add(OperatingSystem.LINUX.getUiString());
            operatingSystemComboBox.add(OperatingSystem.MACOS.getUiString());
            operatingSystemComboBox.add(OperatingSystem.WINDOWS.getUiString());
            for (int i = 0 ; i < operatingSystemComboBox.getItemCount() ; i++) {
            	if (operatingSystemComboBox.getItem(i).equals(operatingSystem.getUiString())) {
            		operatingSystemComboBox.select(i);
            		break;
            	}
            }
            majorVersionComboBox.select(0);
            majorVersionComboBox.notifyListeners(SWT.Selection, new Event());
		});
	}
	
	@Override protected Control createDialogArea(Composite parent) {
		Composite  container = (Composite) super.createDialogArea(parent);
        GridLayout layout    = new GridLayout(2, false);
        container.setLayout(layout);
        
        Button javafxBundledCheckBox = new Button(container, SWT.CHECK);
        javafxBundledCheckBox.setText("JavaFX bundled");
        javafxBundledCheckBox.setLayoutData(new GridData(SWT.RIGHT, SWT.CENTER, true, false, 2, 1));
        javafxBundledCheckBox.addSelectionListener(new SelectionListener() {
			@Override public void widgetSelected(SelectionEvent e) {
				selectedPkgs.clear();
				String[] items         = majorVersionComboBox.getItems();
				int      selectedIndex = majorVersionComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				MajorVersion selectedMajorVersion = maintainedVersions.get(selectedIndex);
				display.asyncExec(() -> majorVersionComboBox.select(selectedIndex));
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
				String[] items         = majorVersionComboBox.getItems();
				int      selectedIndex = majorVersionComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }				
				MajorVersion selectedMajorVersion = maintainedVersions.get(selectedIndex);
				final boolean include_build = selectedMajorVersion.isEarlyAccessOnly();
				List<String> versionList = selectedMajorVersion.getVersions()
										                       .stream()
										                       .sorted(Comparator.comparing(SemVer::getVersionNumber).reversed())
										                       .map(semVer -> semVer.getVersionNumber().toString(OutputFormat.REDUCED_COMPRESSED, true, include_build))
										                       .distinct()
										                       .collect(Collectors.toList());
				display.asyncExec(() -> {
				    versionNumberComboBox.removeAll();
				    versionList.forEach(version -> versionNumberComboBox.add(version));
				    versionNumberComboBox.select(0);
				    versionNumberComboBox.notifyListeners(SWT.Selection, new Event());
				});
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
				String[] items         = versionNumberComboBox.getItems();
				int      selectedIndex = versionNumberComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				
				display.asyncExec(() -> {
					distributionComboBox.removeAll();
					SemVer selectedSemver = SemVer.fromText(items[selectedIndex]).getSemVer1();
					List<Distribution> distributions = discoClient.getDistributionsThatSupport(selectedSemver, null, null, null, null, PackageType.JDK, javafxBundledCheckBox.getSelection(), true);
					
					distributions.stream()
                    			 .sorted(Comparator.comparing(Distribution::getName).reversed())
                    			 .forEach(distro -> distributionComboBox.add(distro.getUiString()));
					
				    distributionComboBox.select(0);		
				    distributionComboBox.notifyListeners(SWT.Selection, new Event());
				});
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
				
				String[] items         = distributionComboBox.getItems();
				int      selectedIndex = distributionComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				
				display.asyncExec(() -> {
					Distribution  distribution = DiscoClient.getDistributionFromText(items[selectedIndex]);
					VersionNumber version      = VersionNumber.fromText(versionNumberComboBox.getItems()[versionNumberComboBox.getSelectionIndex()]);
					List<Pkg> pkgs = discoClient.getPkgs(distribution, version, null, null, null, null, null, null, PackageType.JDK, javafxBundledCheckBox.getSelection(), true, null, null, Scope.BUILD_OF_OPEN_JDK);
					selectedPkgs.clear();
					selectedPkgs.addAll(pkgs);
					Set<OperatingSystem> operatingSystemsForSelectedDistribution = new TreeSet<>();
					selectedPkgs.forEach(pkg -> operatingSystemsForSelectedDistribution.add(pkg.getOperatingSystem()));
					operatingSystemComboBox.removeAll();
					operatingSystemsForSelectedDistribution.forEach(os -> operatingSystemComboBox.add(os.getUiString()));
					operatingSystemComboBox.select(0);
					operatingSystemComboBox.notifyListeners(SWT.Selection, new Event());
				});
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
				
				String[] items         = operatingSystemComboBox.getItems();
				int      selectedIndex = operatingSystemComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				
				display.asyncExec(() -> {
					Set<LibCType> libcTypesForSelectedOperatingSystem = new TreeSet<>();
	                selectedPkgs.stream()
	                            .filter(pkg -> pkg.getOperatingSystem() == OperatingSystem.fromText(items[selectedIndex]))
	                            .map(pkg -> pkg.getLibCType())
	                            .forEach(libcType -> libcTypesForSelectedOperatingSystem.add(libcType));
	                libcTypeComboBox.removeAll();
	                libcTypesForSelectedOperatingSystem.forEach(libcType -> libcTypeComboBox.add(libcType.getUiString()));
	                libcTypeComboBox.select(0);
	                libcTypeComboBox.notifyListeners(SWT.Selection, new Event());
				});
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
				
				String[] items         = libcTypeComboBox.getItems();
				int      selectedIndex = libcTypeComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				
				String[] osItems         = operatingSystemComboBox.getItems();
				int      selectedOsIndex = operatingSystemComboBox.getSelectionIndex();
				if (osItems.length == 0 || selectedOsIndex == -1) { return; }
				
				display.asyncExec(() -> {
					Set<Architecture> architecturesForSelectedOperatingSystem = new TreeSet<>();
	                selectedPkgs.stream()
	                            .filter(pkg -> pkg.getOperatingSystem() == OperatingSystem.fromText(osItems[selectedOsIndex]))
	                            .filter(pkg -> pkg.getLibCType()        == LibCType.fromText(items[selectedIndex]))
	                            .map(pkg -> pkg.getArchitecture())
	                            .forEach(architecture -> architecturesForSelectedOperatingSystem.add(architecture));
	                architectureComboBox.removeAll();
	                architecturesForSelectedOperatingSystem.forEach(architecture -> architectureComboBox.add(architecture.getUiString()));
	                architectureComboBox.select(0);
	                architectureComboBox.notifyListeners(SWT.Selection, new Event());
				});
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
				
				String[] items         = architectureComboBox.getItems();
				int      selectedIndex = architectureComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				
				String[] osItems         = operatingSystemComboBox.getItems();
				int      selectedOsIndex = operatingSystemComboBox.getSelectionIndex();
				if (osItems.length == 0 || selectedOsIndex == -1) { return; }
				
				String[] libcItems         = libcTypeComboBox.getItems();
				int      selectedLibcIndex = libcTypeComboBox.getSelectionIndex();
				if (libcItems.length == 0 || selectedLibcIndex == -1) { return; }
				
				display.asyncExec(() -> {
					Set<ArchiveType> archiveTypesForSelectedArchitecture = new TreeSet<>();
	                selectedPkgs.stream()
	                            .filter(pkg -> pkg.getOperatingSystem() == OperatingSystem.fromText(osItems[selectedOsIndex]))
	                            .filter(pkg -> pkg.getLibCType()        == LibCType.fromText(libcItems[selectedLibcIndex]))
	                            .filter(pkg -> pkg.getArchitecture()    == Architecture.fromText(items[selectedIndex]))
	                            .map(pkg -> pkg.getArchiveType())
	                            .forEach(archiveType -> archiveTypesForSelectedArchitecture.add(archiveType));
	                archiveTypeComboBox.removeAll();
	                archiveTypesForSelectedArchitecture.forEach(archiveType -> archiveTypeComboBox.add(archiveType.getUiString()));
	                archiveTypeComboBox.select(0);
	                archiveTypeComboBox.notifyListeners(SWT.Selection, new Event());
				});
				
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
				String[] items         = archiveTypeComboBox.getItems();
				int      selectedIndex = archiveTypeComboBox.getSelectionIndex();
				if (items.length == 0 || selectedIndex == -1) { return; }
				
				String[] osItems         = operatingSystemComboBox.getItems();
				int      selectedOsIndex = operatingSystemComboBox.getSelectionIndex();
				if (osItems.length == 0 || selectedOsIndex == -1) { return; }
				
				String[] libcItems         = libcTypeComboBox.getItems();
				int      selectedLibcIndex = libcTypeComboBox.getSelectionIndex();
				if (libcItems.length == 0 || selectedLibcIndex == -1) { return; }
				
				String[] arcItems         = architectureComboBox.getItems();
				int      selectedArcIndex = architectureComboBox.getSelectionIndex();
				if (arcItems.length == 0 || selectedArcIndex == -1) { return; }
				
				Optional<Pkg> optionalPkg = selectedPkgs.stream()
								                        .filter(pkg -> pkg.getOperatingSystem() == OperatingSystem.fromText(osItems[selectedOsIndex]))
								                        .filter(pkg -> pkg.getLibCType()        == LibCType.fromText(libcItems[selectedLibcIndex]))
								                        .filter(pkg -> pkg.getArchitecture()    == Architecture.fromText(arcItems[selectedArcIndex]))
								                        .filter(pkg -> pkg.getArchiveType()     == ArchiveType.fromText(items[selectedIndex]))
								                        .findFirst();
				display.asyncExec(() -> {
					if (optionalPkg.isPresent()) {
		                selectedPkg = optionalPkg.get();
	                    filenameLabel.setText(selectedPkg.getFileName());
	                    downloadButton.setEnabled(true);
		            } else {
		                selectedPkg = null;
		                filenameLabel.setText("-");
		                downloadButton.setEnabled(false);
		            }
				});
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
