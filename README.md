# AEM Custom Akamai Replication Agent

This project is a demonstration on how to create a custom replication agent in Adobe Experience Manager.

## Modules

The main parts of the template are:

* core: Java bundle containing the custom TransportHandler and ContentBuilder classes
* ui.apps: contains the custom replication agent component and template

## Requirements

The project was created and tested with the listed requirements although it can be altered to for other versions.

* Java 1.7
* AEM 6.1

## How to build

To build all the modules run in the project root directory the following command with Maven 3:

    mvn clean install

If you have a running AEM instance you can build and package the whole project and deploy into AEM with  

    mvn clean install -PautoInstallPackage
    
Or to deploy it to a publish instance, run

    mvn clean install -PautoInstallPackagePublish
    
Or to deploy only the bundle to the author, run

    mvn clean install -PautoInstallBundle

## Maven settings

The project comes with the auto-public repository configured. To setup the repository in your Maven settings, refer to:

    http://helpx.adobe.com/experience-manager/kb/SetUpTheAdobeMavenRepository.html
