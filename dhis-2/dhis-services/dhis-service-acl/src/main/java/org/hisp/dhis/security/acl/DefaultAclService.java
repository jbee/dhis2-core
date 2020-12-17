package org.hisp.dhis.security.acl;

/*
 * Copyright (c) 2004-2020, University of Oslo
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

import org.hisp.dhis.category.CategoryOption;
import org.hisp.dhis.category.CategoryOptionCombo;
import org.hisp.dhis.common.BaseIdentifiableObject;
import org.hisp.dhis.common.IdentifiableObject;
import org.hisp.dhis.feedback.ErrorCode;
import org.hisp.dhis.feedback.ErrorReport;
import org.hisp.dhis.hibernate.HibernateProxyUtils;
import org.hisp.dhis.schema.Schema;
import org.hisp.dhis.schema.SchemaService;
import org.hisp.dhis.security.AuthorityType;
import org.hisp.dhis.security.acl.AccessStringHelper.Permission;
import org.hisp.dhis.user.User;
import org.hisp.dhis.user.UserAccess;
import org.hisp.dhis.user.UserGroupAccess;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.springframework.util.CollectionUtils.containsAny;

/**
 * Default ACL implementation that uses SchemaDescriptors to get authorities / sharing flags.
 *
 * @author Morten Olav Hansen <mortenoh@gmail.com>
 */
@Service( "org.hisp.dhis.security.acl.AclService" )
public class DefaultAclService implements AclService
{
    private final SchemaService schemaService;

    public DefaultAclService( SchemaService schemaService )
    {
        checkNotNull( schemaService );

        this.schemaService = schemaService;
    }

    @Override
    public boolean isSupported( String type )
    {
        return schemaService.getSchemaBySingularName( type ) != null;
    }

    @Override
    public boolean isSupported( Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        return schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) ) != null;
    }

    @Override
    public boolean isShareable( String type )
    {
        Schema schema = schemaService.getSchemaBySingularName( type );
        return schema != null && schema.isShareable();
    }

    @Override
    public boolean isShareable( Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );
        return schema != null && schema.isShareable();
    }

    @Override
    public boolean isClassShareable( Class clazz )
    {
        Schema schema = schemaService.getSchema( clazz );
        return schema != null && schema.isShareable();
    }

    @Override
    public boolean isDataShareable( Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );
        return schema != null && schema.isDataShareable();
    }

    @Override
    public boolean isDataClassShareable( Class clazz )
    {
        Schema schema = schemaService.getSchema( clazz );
        return schema != null && schema.isDataShareable();
    }

    @Override
    public boolean canRead( User user, IdentifiableObject object )
    {
        if ( readWriteCommonCheck( user, object ) )
        {
            return true;
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        if ( canAccess( user, schema.getAuthorityByType( AuthorityType.READ ) ) )
        {
            if ( object instanceof CategoryOptionCombo )
            {
                return checkOptionComboSharingPermission( user, object, Permission.READ );
            }

            if ( !schema.isShareable() || object.getPublicAccess() == null || checkUser( user, object )
                || checkSharingPermission( user, object, Permission.READ ) )
            {
                return true;
            }
        }
        else
        {
            return false;
        }

        return false;
    }

    @Override
    public boolean canDataRead( User user, IdentifiableObject object )
    {
        if ( readWriteCommonCheck( user, object ) ) return true;

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        if ( canAccess( user, schema.getAuthorityByType( AuthorityType.DATA_READ ) ) )
        {
            if ( object instanceof CategoryOptionCombo )
            {
                return checkOptionComboSharingPermission( user, object, Permission.DATA_READ ) || checkOptionComboSharingPermission( user, object, Permission.DATA_WRITE );
            }

            if ( schema.isDataShareable() &&
                ( checkSharingPermission( user, object, Permission.DATA_READ )
                    || checkSharingPermission( user, object, Permission.DATA_WRITE )) )
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canDataOrMetadataRead( User user, IdentifiableObject object )
    {
        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        return schema.isDataShareable() ? canDataRead( user, object ) : canRead( user, object );
    }

    @Override
    public boolean canWrite( User user, IdentifiableObject object )
    {
        if ( readWriteCommonCheck( user, object ) )
        {
            return true;
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        List<String> anyAuthorities = new ArrayList<>( schema.getAuthorityByType( AuthorityType.CREATE ) );

        if ( anyAuthorities.isEmpty() )
        {
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE_PRIVATE ) );
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE_PUBLIC ) );
        }

        if ( canAccess( user, anyAuthorities ) )
        {
            if ( object instanceof CategoryOptionCombo )
            {
                return checkOptionComboSharingPermission( user, object, Permission.WRITE );
            }

            return writeCommonCheck(schema, user, object);
        }
        else if ( schema.isImplicitPrivateAuthority() && checkSharingAccess( user, object ) )
        {
            return true;
        }

        return false;
    }

    @Override
    public boolean canDataWrite( User user, IdentifiableObject object )
    {
        if ( readWriteCommonCheck( user, object ) )
        {
            return true;
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        // returned unmodifiable list does not need to be cloned since it is not modified
        List<String> anyAuthorities = schema.getAuthorityByType( AuthorityType.DATA_CREATE );

        if ( canAccess( user, anyAuthorities ) )
        {
            if ( object instanceof CategoryOptionCombo )
            {
                return checkOptionComboSharingPermission( user, object, Permission.DATA_WRITE );
            }

            if ( schema.isDataShareable() && checkSharingPermission( user, object, Permission.DATA_WRITE ) )
            {
                return true;
            }
        }

        return false;
    }

    @Override
    public boolean canUpdate( User user, IdentifiableObject object )
    {
        if ( readWriteCommonCheck( user, object ) )
        {
            return true;
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        List<String> anyAuthorities = new ArrayList<>( schema.getAuthorityByType( AuthorityType.UPDATE ) );

        if ( anyAuthorities.isEmpty() )
        {
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE ) );
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE_PRIVATE ) );
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE_PUBLIC ) );
        }

        if ( canAccess( user, anyAuthorities ) )
        {
            return writeCommonCheck( schema, user, object );
        }
        else if ( schema.isImplicitPrivateAuthority() && checkSharingAccess( user, object )
            && (checkUser( user, object ) || checkSharingPermission( user, object, Permission.WRITE )) )
        {
            return true;
        }

        return false;
    }

    @Override
    public boolean canDelete( User user, IdentifiableObject object )
    {
        if ( readWriteCommonCheck( user, object ) )
        {
            return true;
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        List<String> anyAuthorities = new ArrayList<>( schema.getAuthorityByType( AuthorityType.DELETE ) );

        if ( anyAuthorities.isEmpty() )
        {
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE ) );
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE_PRIVATE ) );
            anyAuthorities.addAll( schema.getAuthorityByType( AuthorityType.CREATE_PUBLIC ) );
        }

        if ( canAccess( user, anyAuthorities ) )
        {
            if ( !schema.isShareable() || object.getPublicAccess() == null )
            {
                return true;
            }

            if ( checkSharingAccess( user, object ) &&
                (checkUser( user, object ) || checkSharingPermission( user, object, Permission.WRITE )) )
            {
                return true;
            }
        }
        else if ( schema.isImplicitPrivateAuthority() && ( checkUser( user, object ) || checkSharingPermission( user, object, Permission.WRITE ) ) )
        {
            return true;
        }

        return false;
    }

    @Override
    public boolean canManage( User user, IdentifiableObject object )
    {
        return canUpdate( user, object );
    }

    @Override
    public <T extends IdentifiableObject> boolean canRead( User user, Class<T> klass )
    {
        Schema schema = schemaService.getSchema( klass );

        return schema == null || schema.getAuthorityByType( AuthorityType.READ ) == null
            || canAccess( user, schema.getAuthorityByType( AuthorityType.READ ) );
    }

    @Override
    public <T extends IdentifiableObject> boolean canCreate( User user, Class<T> klass )
    {
        Schema schema = schemaService.getSchema( klass );

        if ( schema == null )
        {
            return false;
        }

        if ( !schema.isShareable() )
        {
            return canAccess( user, schema.getAuthorityByType( AuthorityType.CREATE ) );
        }

        return canMakeClassPublic( user, klass ) || canMakeClassPrivate( user, klass );
    }

    @Override
    public <T extends IdentifiableObject> boolean canMakePublic( User user, Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );
        return !(schema == null || !schema.isShareable())
            && canAccess( user, schema.getAuthorityByType( AuthorityType.CREATE_PUBLIC ) );
    }

    @Override
    public <T extends IdentifiableObject> boolean canMakeClassPublic( User user, Class<T> klass ){
        Schema schema = schemaService.getSchema( klass );
        return !(schema == null || !schema.isShareable())
            && canAccess( user, schema.getAuthorityByType( AuthorityType.CREATE_PUBLIC ) );
    }

    @Override
    public <T extends IdentifiableObject> boolean canMakePrivate( User user, Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object )  );
        return !(schema == null || !schema.isShareable())
            && canAccess( user, schema.getAuthorityByType( AuthorityType.CREATE_PRIVATE ) );
    }

    @Override
    public <T extends IdentifiableObject> boolean canMakeClassPrivate( User user, Class<T> klass )
    {
        Schema schema = schemaService.getSchema( klass  );
        return !(schema == null || !schema.isShareable())
            && canAccess( user, schema.getAuthorityByType( AuthorityType.CREATE_PRIVATE ) );
    }

    @Override
    public <T extends IdentifiableObject> boolean canMakeExternal( User user, Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );
        return !(schema == null || !schema.isShareable())
            && ((!schema.getAuthorityByType( AuthorityType.EXTERNALIZE ).isEmpty() && haveOverrideAuthority( user ))
            || haveAuthority( user, schema.getAuthorityByType( AuthorityType.EXTERNALIZE ) ));
    }

    @Override
    public <T extends IdentifiableObject> boolean canMakeClassExternal( User user, Class<T> klass )
    {
        Schema schema = schemaService.getSchema( klass );
        return !(schema == null || !schema.isShareable())
            && ((!schema.getAuthorityByType( AuthorityType.EXTERNALIZE ).isEmpty() && haveOverrideAuthority( user ))
            || haveAuthority( user, schema.getAuthorityByType( AuthorityType.EXTERNALIZE ) ));
    }


    @Override
    public <T extends IdentifiableObject> boolean defaultPrivate( Class<T> klass )
    {
        Schema schema = schemaService.getSchema( klass );
        return schema != null && schema.isDefaultPrivate();
    }

    @Override
    public <T extends IdentifiableObject> boolean defaultPublic( Object object )
    {
        if ( object instanceof Class )
        {
            throw new IllegalArgumentException( "Input object can't be of type Class!" );
        }

        return !defaultPrivate( HibernateProxyUtils.getRealClass( object ) );
    }

    @Override
    @SuppressWarnings( "unchecked" )
    public Class<? extends IdentifiableObject> classForType( String type )
    {
        Schema schema = schemaService.getSchemaBySingularName( type );

        if ( schema != null && schema.isIdentifiableObject() )
        {
            return (Class<? extends IdentifiableObject>) schema.getKlass();
        }

        return null;
    }

    @Override
    public <T extends IdentifiableObject> Access getAccess( T object, User user )
    {
        if ( user == null || user.isSuper() )
        {
            Access access = new Access( true );

            if ( isDataShareable( object ) )
            {
                access.setData( new AccessData( true, true ) );
            }

            return access;
        }

        Access access = new Access();
        access.setManage( canManage( user, object ) );
        access.setExternalize( canMakeExternal( user, object ) );
        access.setWrite( canWrite( user, object ) );
        access.setRead( canRead( user, object ) );
        access.setUpdate( canUpdate( user, object ) );
        access.setDelete( canDelete( user, object ) );

        if ( isDataShareable( object ) )
        {
            AccessData data = new AccessData( canDataRead( user, object ), canDataWrite( user, object ) );

            access.setData( data );
        }

        return access;
    }

    @Override
    public <T extends IdentifiableObject> void resetSharing( T object, User user )
    {
        if ( object == null || !isShareable( object ) || user == null )
        {
            return;
        }

        BaseIdentifiableObject baseIdentifiableObject = (BaseIdentifiableObject) object;
        baseIdentifiableObject.setPublicAccess( AccessStringHelper.DEFAULT );
        baseIdentifiableObject.setExternalAccess( false );

        if ( object.getUser() == null )
        {
            baseIdentifiableObject.setUser( user );
        }

        if ( canMakePublic( user, object ) )
        {
            if ( defaultPublic( object ) )
            {
                baseIdentifiableObject.setPublicAccess( AccessStringHelper.READ_WRITE );
            }
        }

        object.getUserAccesses().clear();
        object.getUserGroupAccesses().clear();
    }

    @Override
    public <T extends IdentifiableObject> void clearSharing( T object, User user )
    {
        if ( object == null || !isShareable( object ) || user == null )
        {
            return;
        }

        BaseIdentifiableObject baseIdentifiableObject = (BaseIdentifiableObject) object;
        baseIdentifiableObject.setUser( user );
        baseIdentifiableObject.setPublicAccess( AccessStringHelper.DEFAULT );
        baseIdentifiableObject.setExternalAccess( false );

        object.getUserAccesses().clear();
        object.getUserGroupAccesses().clear();
    }

    @Override
    public <T extends IdentifiableObject> List<ErrorReport> verifySharing( T object, User user )
    {
        List<ErrorReport> errorReports = new ArrayList<>();

        if ( object == null || haveOverrideAuthority( user ) || !isShareable( object ) )
        {
            return errorReports;
        }

        if ( !AccessStringHelper.isValid( object.getPublicAccess() ) )
        {
            errorReports.add( new ErrorReport( object.getClass(), ErrorCode.E3010, object.getPublicAccess() ) );
            return errorReports;
        }

        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        if ( !schema.isDataShareable() )
        {
            ErrorReport errorReport = null;

            if ( object.getPublicAccess() != null && AccessStringHelper.hasDataSharing( object.getPublicAccess() ) )
            {
                errorReport = new ErrorReport( object.getClass(), ErrorCode.E3011, object.getClass() );
            }
            else
            {
                for ( UserAccess userAccess : object.getUserAccesses() )
                {
                    if ( AccessStringHelper.hasDataSharing( userAccess.getAccess() ) )
                    {
                        errorReport = new ErrorReport( object.getClass(), ErrorCode.E3011, object.getClass() );
                        break;
                    }
                }

                for ( UserGroupAccess userGroupAccess : object.getUserGroupAccesses() )
                {
                    if ( AccessStringHelper.hasDataSharing( userGroupAccess.getAccess() ) )
                    {
                        errorReport = new ErrorReport( object.getClass(), ErrorCode.E3011, object.getClass() );
                        break;
                    }
                }
            }

            if ( errorReport != null )
            {
                errorReports.add( errorReport );
            }
        }

        boolean canMakePublic = canMakePublic( user, object );
        boolean canMakePrivate = canMakePrivate( user, object );
        boolean canMakeExternal = canMakeExternal( user, object );

        if ( object.getExternalAccess() )
        {
            if ( !canMakeExternal )
            {
                errorReports.add( new ErrorReport( object.getClass(), ErrorCode.E3006, user.getUsername(), object.getClass() ) );
            }
        }

        errorReports.addAll( verifyImplicitSharing( user, object ) );

        if ( AccessStringHelper.DEFAULT.equals( object.getPublicAccess() ) )
        {
            if ( canMakePublic || canMakePrivate )
            {
                return errorReports;
            }

            errorReports.add( new ErrorReport( object.getClass(), ErrorCode.E3009, user.getUsername(), object.getClass() ) );
        }
        else
        {
            if ( canMakePublic )
            {
                return errorReports;
            }

            errorReports.add( new ErrorReport( object.getClass(), ErrorCode.E3008, user.getUsername(), object.getClass() ) );
        }

        return errorReports;
    }

    private <T extends IdentifiableObject> Collection<? extends ErrorReport> verifyImplicitSharing( User user, T object )
    {
        List<ErrorReport> errorReports = new ArrayList<>();
        Schema schema = schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) );

        if ( !schema.isImplicitPrivateAuthority() || checkUser( user, object ) || checkSharingPermission( user, object, Permission.WRITE ) )
        {
            return errorReports;
        }

        if ( AccessStringHelper.DEFAULT.equals( object.getPublicAccess() ) )
        {
            errorReports.add( new ErrorReport( object.getClass(), ErrorCode.E3001, user.getUsername(), object.getClass() ) );
        }

        return errorReports;
    }

    private boolean haveOverrideAuthority( User user )
    {
        return user == null || user.isSuper();
    }

    private boolean canAccess( User user, Collection<String> anyAuthorities )
    {
        return haveOverrideAuthority( user ) || anyAuthorities.isEmpty() || haveAuthority( user, anyAuthorities );
    }

    private boolean haveAuthority( User user, Collection<String> anyAuthorities )
    {
        return containsAny( user.getUserCredentials().getAllAuthorities(), anyAuthorities );
    }

    /**
     * Should user be allowed access to this object.
     *
     * @param user   User to check against
     * @param object Object to check against
     * @return true/false depending on if access should be allowed
     */
    private boolean checkUser( User user, IdentifiableObject object )
    {
        return user == null || object.getUser() == null || user.getUid().equals( object.getUser().getUid() );
    }

    /**
     * Is the current user allowed to create/update the object given based on its sharing settings.
     *
     * @param user   User to check against
     * @param object Object to check against
     * @return true/false depending on if sharing settings are allowed for given user
     */
    private boolean checkSharingAccess( User user, IdentifiableObject object )
    {
        boolean canMakePublic = canMakePublic( user, object );
        boolean canMakePrivate = canMakePrivate( user, object );
        boolean canMakeExternal = canMakeExternal( user, object );

        if ( AccessStringHelper.DEFAULT.equals( object.getPublicAccess() ) )
        {
            if ( !(canMakePublic || canMakePrivate) )
            {
                return false;
            }
        }
        else
        {
            if ( !canMakePublic )
            {
                return false;
            }
        }

        if ( object.getExternalAccess() && !canMakeExternal )
        {
            return false;
        }

        return true;
    }

    /**
     * If the given user allowed to access the given object using the permissions given.
     *
     * @param user       User to check against
     * @param object     Object to check against
     * @param permission Permission to check against
     * @return true if user can access object, false otherwise
     */
    private boolean checkSharingPermission( User user, IdentifiableObject object, Permission permission )
    {
        if ( AccessStringHelper.isEnabled( object.getPublicAccess(), permission ) )
        {
            return true;
        }

        for ( UserGroupAccess userGroupAccess : object.getUserGroupAccesses() )
        {
            // Check if user is allowed to read this object through group access

            if ( AccessStringHelper.isEnabled( userGroupAccess.getAccess(), permission )
                    && userGroupAccess.userGroupContainsUser( user ) )
            {
                return true;
            }
        }

        for ( UserAccess userAccess : object.getUserAccesses() )
        {
            // Check if user is allowed to read to this object through user access

            if ( AccessStringHelper.isEnabled( userAccess.getAccess(), permission )
                    && user.equals( userAccess.getUser() ) )
            {
                return true;
            }
        }

        return false;
    }

    private boolean checkOptionComboSharingPermission( User user, IdentifiableObject object, Permission permission )
    {
        CategoryOptionCombo optionCombo = (CategoryOptionCombo) object;

        if ( optionCombo.isDefault() || optionCombo.getCategoryOptions().isEmpty() )
        {
            return true;
        }

        List<Long> accessibleOptions = new ArrayList<>();

        for ( CategoryOption option : optionCombo.getCategoryOptions() )
        {
            if ( checkSharingPermission( user, option, permission ) )
            {
                accessibleOptions.add( option.getId() );
            }
        }

        return accessibleOptions.size() == optionCombo.getCategoryOptions().size();
    }

    private boolean readWriteCommonCheck( User user, IdentifiableObject object )
    {
        if ( object == null || haveOverrideAuthority( user ) )
        {
            return true;
        }

        return schemaService.getSchema( HibernateProxyUtils.getRealClass( object ) ) == null;
    }

    private boolean writeCommonCheck( Schema schema, User user, IdentifiableObject object )
    {
        if ( !schema.isShareable() )
        {
            return true;
        }

        return checkSharingAccess(user, object) &&
            ( checkUser(user, object) || checkSharingPermission( user, object, Permission.WRITE ) );
    }
}
