/*
 * Copyright (c) 2004-2022, University of Oslo
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 * Redistributions of source code must retain the above copyright notice, this
 * list of conditions and the following disclaimer.
 *
 * Redistributions in binary form must reproduce the above copyright notice,
 * this list of conditions and the following disclaimer in the documentation
 * and/or other materials provided with the distribution.
 * Neither the name of the HISP project nor the names of its contributors may
 * be used to endorse or promote products derived from this software without
 * specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND
 * ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED
 * WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR
 * ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON
 * ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS
 * SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package org.hisp.dhis.webapi.controller.user;

import static org.hisp.dhis.user.User.populateUserCredentialsDtoFields;
import static org.hisp.dhis.webapi.utils.ContextUtils.setNoStore;
import static org.springframework.http.CacheControl.noStore;
import static org.springframework.http.MediaType.APPLICATION_JSON_VALUE;

import java.io.IOException;
import java.io.Serializable;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import javax.annotation.Nonnull;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import lombok.RequiredArgsConstructor;

import org.apache.commons.lang3.StringUtils;
import org.hisp.dhis.common.BaseIdentifiableObject;
import org.hisp.dhis.common.DhisApiVersion;
import org.hisp.dhis.common.IdentifiableObjectManager;
import org.hisp.dhis.common.OpenApi;
import org.hisp.dhis.dataapproval.DataApprovalLevel;
import org.hisp.dhis.dataapproval.DataApprovalLevelService;
import org.hisp.dhis.dataset.DataSetService;
import org.hisp.dhis.feedback.ConflictException;
import org.hisp.dhis.feedback.NotFoundException;
import org.hisp.dhis.fieldfiltering.FieldFilterService;
import org.hisp.dhis.fileresource.FileResource;
import org.hisp.dhis.fileresource.FileResourceService;
import org.hisp.dhis.interpretation.InterpretationService;
import org.hisp.dhis.message.MessageService;
import org.hisp.dhis.node.NodeService;
import org.hisp.dhis.node.NodeUtils;
import org.hisp.dhis.node.Preset;
import org.hisp.dhis.node.types.CollectionNode;
import org.hisp.dhis.node.types.RootNode;
import org.hisp.dhis.node.types.SimpleNode;
import org.hisp.dhis.program.ProgramService;
import org.hisp.dhis.render.RenderService;
import org.hisp.dhis.security.PasswordManager;
import org.hisp.dhis.security.acl.Access;
import org.hisp.dhis.security.acl.AclService;
import org.hisp.dhis.system.util.ValidationUtils;
import org.hisp.dhis.user.CredentialsInfo;
import org.hisp.dhis.user.CurrentUser;
import org.hisp.dhis.user.PasswordValidationResult;
import org.hisp.dhis.user.PasswordValidationService;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserCredentialsDto;
import org.hisp.dhis.user.UserService;
import org.hisp.dhis.user.UserSettingKey;
import org.hisp.dhis.user.UserSettingService;
import org.hisp.dhis.webapi.mvc.annotation.ApiVersion;
import org.hisp.dhis.webapi.service.ContextService;
import org.hisp.dhis.webapi.webdomain.Dashboard;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.switchuser.SwitchUserGrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.ResponseStatus;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.common.collect.Lists;
import com.google.common.collect.Sets;

/**
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
@OpenApi.Tags( { "user", "query" } )
@Controller
@ApiVersion( { DhisApiVersion.DEFAULT, DhisApiVersion.ALL } )
@RequestMapping( "/me" )
@RequiredArgsConstructor
public class MeController
{
    @Nonnull
    private final UserService userService;

    @Nonnull
    private final UserControllerUtils userControllerUtils;

    @Nonnull
    protected ContextService contextService;

    @Nonnull
    private final RenderService renderService;

    @Nonnull
    private final FieldFilterService fieldFilterService;

    @Nonnull
    private final org.hisp.dhis.fieldfilter.FieldFilterService oldFieldFilterService;

    @Nonnull
    private final IdentifiableObjectManager manager;

    @Nonnull
    private final PasswordManager passwordManager;

    @Nonnull
    private final MessageService messageService;

    @Nonnull
    private final InterpretationService interpretationService;

    @Nonnull
    private final NodeService nodeService;

    @Nonnull
    private final UserSettingService userSettingService;

    @Nonnull
    private final PasswordValidationService passwordValidationService;

    @Nonnull
    private final ProgramService programService;

    @Nonnull
    private final DataSetService dataSetService;

    @Nonnull
    private final AclService aclService;

    @Nonnull
    private final DataApprovalLevelService approvalLevelService;

    @Nonnull
    private final FileResourceService fileResourceService;

    private static final Set<UserSettingKey> USER_SETTING_KEYS = new HashSet<>(
        Sets.newHashSet( UserSettingKey.values() ) );

    @GetMapping
    @OpenApi.Response( MeDto.class )
    public @ResponseBody ResponseEntity<JsonNode> getCurrentUser( @CurrentUser( required = true ) User user,
        @RequestParam( defaultValue = "*" ) List<String> fields )
    {
        if ( fieldsContains( "access", fields ) )
        {
            Access access = aclService.getAccess( user, user );
            user.setAccess( access );
        }

        Map<String, Serializable> userSettings = userSettingService.getUserSettingsWithFallbackByUserAsMap(
            user, USER_SETTING_KEYS, true );

        List<String> programs = programService.getCurrentUserPrograms().stream().map( BaseIdentifiableObject::getUid )
            .collect( Collectors.toList() );

        List<String> dataSets = dataSetService.getUserDataRead( user ).stream().map( BaseIdentifiableObject::getUid )
            .collect( Collectors.toList() );

        MeDto meDto = new MeDto( user, userSettings, programs, dataSets );
        determineUserImpersonation( meDto );

        // TODO: To remove when we remove old UserCredentials compatibility
        UserCredentialsDto userCredentialsDto = user.getUserCredentials();
        meDto.setUserCredentials( userCredentialsDto );

        var params = org.hisp.dhis.fieldfiltering.FieldFilterParams.of( meDto, fields );
        ObjectNode jsonNodes = fieldFilterService.toObjectNodes( params ).get( 0 );

        return ResponseEntity.ok( jsonNodes );
    }

    private void determineUserImpersonation( MeDto meDto )
    {
        Authentication current = SecurityContextHolder.getContext().getAuthentication();

        Authentication original = null;
        // iterate over granted authorities and find the 'switch user' authority
        Collection<? extends GrantedAuthority> authorities = current.getAuthorities();
        for ( GrantedAuthority auth : authorities )
        {
            // check for switch user type of authority
            if ( auth instanceof SwitchUserGrantedAuthority )
            {
                original = ((SwitchUserGrantedAuthority) auth).getSource();
                meDto.setImpersonation( original.getName() );
            }
        }
    }

    private boolean fieldsContains( String key, List<String> fields )
    {
        for ( String field : fields )
        {
            if ( field.contains( key ) || field.equals( "*" ) || field.startsWith( ":" ) )
            {
                return true;
            }
        }

        return false;
    }

    @GetMapping( "/dataApprovalWorkflows" )
    public ResponseEntity<ObjectNode> getCurrentUserDataApprovalWorkflows( HttpServletResponse response,
        @CurrentUser( required = true ) User user )
    {
        ObjectNode objectNode = userControllerUtils.getUserDataApprovalWorkflows( user );
        return ResponseEntity.ok( objectNode );
    }

    @PutMapping( value = "", consumes = APPLICATION_JSON_VALUE )
    public void updateCurrentUser( HttpServletRequest request, HttpServletResponse response,
        @CurrentUser( required = true ) User currentUser )
        throws ConflictException,
        IOException

    {
        List<String> fields = Lists.newArrayList( contextService.getParameterValues( "fields" ) );

        User user = renderService.fromJson( request.getInputStream(), User.class );

        // TODO: To remove when we remove old UserCredentials compatibility
        populateUserCredentialsDtoFields( user );

        merge( currentUser, user );

        if ( user.getWhatsApp() != null && !ValidationUtils.validateWhatsApp( user.getWhatsApp() ) )
        {
            throw new ConflictException( "Invalid format for WhatsApp value '" + user.getWhatsApp() + "'" );
        }

        FileResource avatar = currentUser.getAvatar();
        if ( avatar != null )
        {
            FileResource fileResource = fileResourceService.getFileResource( avatar.getUid() );
            if ( fileResource == null )
            {
                throw new ConflictException( "File does not exist" );
            }

            if ( !fileResource.getCreatedBy().getUid().equals( currentUser.getUid() ) )
            {
                throw new ConflictException( "Not the owner of the file" );
            }

            currentUser.setAvatar( fileResource );
        }

        manager.update( currentUser );

        if ( fields.isEmpty() )
        {
            fields.addAll( Preset.ALL.getFields() );
        }

        CollectionNode collectionNode = oldFieldFilterService.toCollectionNode( User.class,
            new org.hisp.dhis.fieldfilter.FieldFilterParams( Collections.singletonList( currentUser ), fields ) );

        response.setContentType( APPLICATION_JSON_VALUE );
        nodeService.serialize( NodeUtils.createRootNode( collectionNode.getChildren().get( 0 ) ),
            APPLICATION_JSON_VALUE,
            response.getOutputStream() );
    }

    @GetMapping( value = { "/authorization", "/authorities" }, produces = APPLICATION_JSON_VALUE )
    public ResponseEntity<Set<String>> getAuthorities( @CurrentUser( required = true ) User currentUser )
    {
        return ResponseEntity.ok().cacheControl( noStore() )
            .body( currentUser.getAllAuthorities() );
    }

    @GetMapping( value = { "/authorization/{authority}",
        "/authorities/{authority}" }, produces = APPLICATION_JSON_VALUE )
    public ResponseEntity<Boolean> hasAuthority( @PathVariable String authority,
        @CurrentUser( required = true ) User currentUser )
    {
        return ResponseEntity.ok().cacheControl( noStore() )
            .body( currentUser.isAuthorized( authority ) );
    }

    @GetMapping( value = "/settings", produces = APPLICATION_JSON_VALUE )
    public ResponseEntity<Map<String, Serializable>> getSettings( @CurrentUser( required = true ) User currentUser )
    {
        Map<String, Serializable> userSettings = userSettingService.getUserSettingsWithFallbackByUserAsMap(
            currentUser, USER_SETTING_KEYS, true );

        return ResponseEntity.ok().cacheControl( noStore() ).body( userSettings );
    }

    @GetMapping( value = "/settings/{key}", produces = APPLICATION_JSON_VALUE )
    public ResponseEntity<Serializable> getSetting( @PathVariable String key,
        @CurrentUser( required = true ) User currentUser )
        throws ConflictException,
        NotFoundException
    {
        Optional<UserSettingKey> keyEnum = UserSettingKey.getByName( key );

        if ( keyEnum.isEmpty() )
        {
            throw new ConflictException( "Key is not supported: " + key );
        }

        Serializable value = userSettingService.getUserSetting( keyEnum.get(), currentUser );

        if ( value == null )
        {
            throw new NotFoundException( "User setting not found for key: " + key );
        }

        return ResponseEntity.ok().cacheControl( noStore() ).body( value );
    }

    @PutMapping( value = "/changePassword", consumes = { "text/*", "application/*" } )
    @ResponseStatus( HttpStatus.ACCEPTED )
    public void changePassword( @RequestBody Map<String, String> body,
        @CurrentUser( required = true ) User currentUser )
        throws ConflictException
    {
        String oldPassword = body.get( "oldPassword" );
        String newPassword = body.get( "newPassword" );

        if ( StringUtils.isEmpty( oldPassword ) || StringUtils.isEmpty( newPassword ) )
        {
            throw new ConflictException( "OldPassword and newPassword must be provided" );
        }

        boolean valid = passwordManager.matches( oldPassword, currentUser.getPassword() );

        if ( !valid )
        {
            throw new ConflictException( "OldPassword is incorrect" );
        }

        updatePassword( currentUser, newPassword );
        manager.update( currentUser );

        userService.expireActiveSessions( currentUser );
    }

    @PostMapping( value = "/verifyPassword", consumes = "text/*" )
    public @ResponseBody RootNode verifyPasswordText( @RequestBody String password, HttpServletResponse response,
        @CurrentUser( required = true ) User currentUser )
        throws ConflictException
    {
        return verifyPasswordInternal( password, currentUser );
    }

    @PostMapping( value = "/validatePassword", consumes = "text/*" )
    public @ResponseBody RootNode validatePasswordText( @RequestBody String password, HttpServletResponse response,
        @CurrentUser( required = true ) User currentUser )
        throws ConflictException
    {
        return validatePasswordInternal( password, currentUser );
    }

    @PostMapping( value = "/verifyPassword", consumes = APPLICATION_JSON_VALUE )
    public @ResponseBody RootNode verifyPasswordJson( @RequestBody Map<String, String> body,
        HttpServletResponse response, @CurrentUser( required = true ) User currentUser )
        throws ConflictException
    {
        return verifyPasswordInternal( body.get( "password" ), currentUser );
    }

    @GetMapping( "/dashboard" )
    public @ResponseBody Dashboard getDashboard( HttpServletResponse response,
        @CurrentUser( required = true ) User currentUser )
    {
        Dashboard dashboard = new Dashboard();
        dashboard.setUnreadMessageConversations( messageService.getUnreadMessageConversationCount() );
        dashboard.setUnreadInterpretations( interpretationService.getNewInterpretationCount() );

        setNoStore( response );
        return dashboard;
    }

    @PostMapping( value = "/dashboard/interpretations/read" )
    @ResponseStatus( value = HttpStatus.NO_CONTENT )
    @ApiVersion( include = { DhisApiVersion.ALL, DhisApiVersion.DEFAULT } )
    public void updateInterpretationsLastRead()
    {
        interpretationService.updateCurrentUserLastChecked();
    }

    @GetMapping( value = "/dataApprovalLevels", produces = { APPLICATION_JSON_VALUE, "text/*" } )
    public ResponseEntity<List<DataApprovalLevel>> getApprovalLevels( @CurrentUser User currentUser )
    {
        List<DataApprovalLevel> approvalLevels = approvalLevelService
            .getUserDataApprovalLevels( currentUser );
        return ResponseEntity.ok().cacheControl( noStore() ).body( approvalLevels );
    }

    // ------------------------------------------------------------------------------------------------
    // Supportive methods
    // ------------------------------------------------------------------------------------------------

    private RootNode verifyPasswordInternal( String password, User currentUser )
        throws ConflictException
    {
        if ( password == null )
        {
            throw new ConflictException( "Required attribute 'password' missing or null." );
        }

        boolean valid = passwordManager.matches( password, currentUser.getPassword() );

        RootNode rootNode = NodeUtils.createRootNode( "response" );
        rootNode.addChild( new SimpleNode( "isCorrectPassword", valid ) );

        return rootNode;
    }

    private RootNode validatePasswordInternal( String password, User currentUser )
        throws ConflictException
    {
        if ( password == null )
        {
            throw new ConflictException( "Required attribute 'password' missing or null." );
        }

        CredentialsInfo credentialsInfo = new CredentialsInfo( currentUser.getUsername(), password,
            currentUser.getEmail(), false );

        PasswordValidationResult result = passwordValidationService.validate( credentialsInfo );

        RootNode rootNode = NodeUtils.createRootNode( "response" );
        rootNode.addChild( new SimpleNode( "isValidPassword", result.isValid() ) );

        if ( !result.isValid() )
        {
            rootNode.addChild( new SimpleNode( "errorMessage", result.getErrorMessage() ) );
        }

        return rootNode;
    }

    private void merge( User currentUser, User user )
    {
        currentUser.setFirstName( stringWithDefault( user.getFirstName(), currentUser.getFirstName() ) );
        currentUser.setSurname( stringWithDefault( user.getSurname(), currentUser.getSurname() ) );
        currentUser.setEmail( stringWithDefault( user.getEmail(), currentUser.getEmail() ) );
        currentUser.setPhoneNumber( stringWithDefault( user.getPhoneNumber(), currentUser.getPhoneNumber() ) );
        currentUser.setJobTitle( stringWithDefault( user.getJobTitle(), currentUser.getJobTitle() ) );
        currentUser.setIntroduction( stringWithDefault( user.getIntroduction(), currentUser.getIntroduction() ) );
        currentUser.setGender( stringWithDefault( user.getGender(), currentUser.getGender() ) );

        currentUser.setAvatar( user.getAvatar() != null ? user.getAvatar() : currentUser.getAvatar() );

        currentUser.setSkype( stringWithDefault( user.getSkype(), currentUser.getSkype() ) );
        currentUser.setFacebookMessenger(
            stringWithDefault( user.getFacebookMessenger(), currentUser.getFacebookMessenger() ) );
        currentUser.setTelegram( stringWithDefault( user.getTelegram(), currentUser.getTelegram() ) );
        currentUser.setWhatsApp( stringWithDefault( user.getWhatsApp(), currentUser.getWhatsApp() ) );
        currentUser.setTwitter( stringWithDefault( user.getTwitter(), currentUser.getTwitter() ) );

        if ( user.getBirthday() != null )
        {
            currentUser.setBirthday( user.getBirthday() );
        }

        currentUser.setNationality( stringWithDefault( user.getNationality(), currentUser.getNationality() ) );
        currentUser.setEmployer( stringWithDefault( user.getEmployer(), currentUser.getEmployer() ) );
        currentUser.setEducation( stringWithDefault( user.getEducation(), currentUser.getEducation() ) );
        currentUser.setInterests( stringWithDefault( user.getInterests(), currentUser.getInterests() ) );
        currentUser.setLanguages( stringWithDefault( user.getLanguages(), currentUser.getLanguages() ) );
    }

    private void updatePassword( User currentUser, String password )
        throws ConflictException
    {
        if ( !StringUtils.isEmpty( password ) )
        {
            CredentialsInfo credentialsInfo = new CredentialsInfo( currentUser.getUsername(), password,
                currentUser.getEmail(), false );

            PasswordValidationResult result = passwordValidationService.validate( credentialsInfo );

            if ( result.isValid() )
            {
                userService.encodeAndSetPassword( currentUser, password );
            }
            else
            {
                throw new ConflictException( result.getErrorMessage() );
            }
        }
    }

    private String stringWithDefault( String value, String defaultValue )
    {
        return !StringUtils.isEmpty( value ) ? value : defaultValue;
    }
}
